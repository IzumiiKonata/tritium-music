param(
	[string]$OutputDirectory = (Join-Path $PSScriptRoot "artifacts"),
	[int]$MaxParallel = 4
)

$ErrorActionPreference = "Stop"

$branches = @(
	[pscustomobject]@{ Name = "main"; Directory = "m" },
	[pscustomobject]@{ Name = "1.20.1"; Directory = "a" },
	[pscustomobject]@{ Name = "1.21.11"; Directory = "b" },
	[pscustomobject]@{ Name = "26.2"; Directory = "c" }
)

if ($MaxParallel -lt 1) {
	throw "MaxParallel must be at least 1"
}

function Resolve-Java25Home([string]$InputPath) {
	$path = $InputPath.Trim().Trim('"')

	if ([string]::IsNullOrWhiteSpace($path)) {
		throw "Empty path"
	}

	# Accept:
	#   JDK root
	#   JDK root\bin
	#   JDK root\bin\java.exe
	if (Test-Path -LiteralPath $path -PathType Leaf) {
		if ([System.IO.Path]::GetFileName($path) -ieq "java.exe") {
			$path = Split-Path -Parent $path
		} else {
			throw "The specified file is not java.exe"
		}
	}

	$fullPath = [System.IO.Path]::GetFullPath($path)

	if ((Split-Path -Leaf $fullPath) -ieq "bin") {
		$fullPath = Split-Path -Parent $fullPath
	}

	$javaExe = Join-Path $fullPath "bin\java.exe"
	if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
		throw "Could not find bin\java.exe under '$fullPath'"
	}

	return $fullPath
}

function Get-JavaVersion([string]$JavaExe) {
	$psi = [System.Diagnostics.ProcessStartInfo]::new()
	$psi.FileName = $JavaExe
	$psi.Arguments = "-version"
	$psi.UseShellExecute = $false
	$psi.CreateNoWindow = $true
	$psi.RedirectStandardOutput = $true
	$psi.RedirectStandardError = $true

	$process = [System.Diagnostics.Process]::new()
	$process.StartInfo = $psi
	try {
		[void]$process.Start()
		$stdout = $process.StandardOutput.ReadToEnd()
		$stderr = $process.StandardError.ReadToEnd()
		$process.WaitForExit()
	} finally {
		$process.Dispose()
	}

	$output = $stdout + $stderr

	if ($output -match 'version "([0-9]+)') {
		return [int]$Matches[1]
	}
	if ($output -match 'openjdk ([0-9]+)') {
		return [int]$Matches[1]
	}
	throw "Unable to determine the Java version from $JavaExe"
}

function Get-Java25Home {
	$candidates = @(
		[Environment]::GetEnvironmentVariable("JAVA25_HOME", "User"),
		[Environment]::GetEnvironmentVariable("JAVA25_HOME", "Machine"),
		[Environment]::GetEnvironmentVariable("JAVA_HOME", "Process")
	)

	foreach ($candidate in $candidates) {
		if ([string]::IsNullOrWhiteSpace($candidate)) {
			continue
		}

		try {
			$javaHome = Resolve-Java25Home $candidate
			$javaExe = Join-Path $javaHome "bin\java.exe"

			try {
				$version = Get-JavaVersion $javaExe
			} catch {
				continue
			}

			if ($version -eq 25) {
				return $javaHome
			}
		} catch {
			# Try the next candidate.
		}
	}

	$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
	if ($null -ne $javaCommand) {
		try {
			$javaHome = Resolve-Java25Home (Split-Path -Parent (Split-Path -Parent $javaCommand.Source))

			# Get-JavaVersion reads the streams directly: "java -version" always
			# writes to stderr, and merging that into the pipeline under
			# $ErrorActionPreference = "Stop" raises a terminating
			# NativeCommandError.
			if ((Get-JavaVersion (Join-Path $javaHome "bin\java.exe")) -eq 25) {
				return $javaHome
			}
		} catch {
			# Fall through to interactive setup.
		}
	}

	Write-Host ""
	Write-Host "Java 25 was not found." -ForegroundColor Yellow
	Write-Host "Please enter the Java 25 JDK path."
	Write-Host "You may enter any of these:"
	Write-Host "  C:\Program Files\Java\jdk-25"
	Write-Host "  C:\Program Files\Java\jdk-25\bin"
	Write-Host "  C:\Program Files\Java\jdk-25\bin\java.exe"
	Write-Host ""

	while ($true) {
		$inputPath = Read-Host "Java 25 path"

		try {
			$javaHome = Resolve-Java25Home $inputPath
			$javaExe = Join-Path $javaHome "bin\java.exe"
			$version = Get-JavaVersion $javaExe

			if ($version -ne 25) {
				throw "This is Java $version, not Java 25"
			}

			[Environment]::SetEnvironmentVariable("JAVA25_HOME", $javaHome, "User")
			Write-Host "Java 25 location saved as JAVA25_HOME." -ForegroundColor Green
			return $javaHome
		} catch {
			Write-Host "Invalid Java 25 path: $($_.Exception.Message)" -ForegroundColor Red
			Write-Host "Please try again."
		}
	}
}

function Set-Java25 {
	$javaHome = Get-Java25Home
	$javaExe = Join-Path $javaHome "bin\java.exe"

	$actualVersion = Get-JavaVersion $javaExe
	if ($actualVersion -ne 25) {
		throw "Java 25 is required, but $javaExe is Java $actualVersion"
	}

	$env:JAVA_HOME = $javaHome

	$javaBin = Join-Path $javaHome "bin"
	$pathEntries = $env:Path -split ';' | Where-Object {
		$_ -and ($_ -ne $javaBin)
	}
	$env:Path = $javaBin + ';' + ($pathEntries -join ';')

	Write-Host "Using Java 25: $javaHome"
	return $javaHome
}

# Every branch in this build script is built with Java 25.
$javaHome = Set-Java25

$repositoryRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
	throw "Unable to locate the Git repository"
}

if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
	$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
	$outputPath = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
}

New-Item -ItemType Directory -Path $outputPath -Force | Out-Null

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("tmb-" + [System.Guid]::NewGuid().ToString("N").Substring(0, 8))
$createdWorktrees = @()

# One job builds one worktree. The job does NOT touch the final output directory;
# this avoids concurrent writes to manifest.json and artifact files.
$buildJob = {
	param(
		[string]$BranchName,
		[string]$WorktreePath,
		[string]$JavaHome,
		[string]$LogPath
	)

	$ErrorActionPreference = "Stop"

	$env:JAVA_HOME = $JavaHome
	$env:Path = (Join-Path $JavaHome "bin") + ";" + $env:Path

	if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
		$gradleWrapper = Join-Path $WorktreePath "gradlew.bat"
	} else {
		$gradleWrapper = Join-Path $WorktreePath "gradlew"
	}

	if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
		throw "Gradle wrapper not found: $gradleWrapper"
	}

	$buildTasks = @(":fabric:build", ":neoforge:build")
	$loaders = @("fabric", "neoforge")

	# Only 1.20.1 contains Forge.
	if ($BranchName -eq "1.20.1") {
		$buildTasks += ":forge:build"
		$loaders += "forge"
	}

	$start = Get-Date
	Write-Host "[$BranchName] Build started"

	# Each branch has its own worktree, so these Gradle invocations can run
	# concurrently. --no-daemon also prevents long-lived Gradle daemons.
	# Keep a per-branch log so parallel output does not become unreadable.
	#
	# Gradle writes notices to stderr as well as stdout (the JVM prints
	# "Picked up JAVA_TOOL_OPTIONS", for example). Under
	# $ErrorActionPreference = "Stop" a merged stderr record becomes a
	# terminating NativeCommandError that kills the job before the first line is
	# ever logged, so the preference is relaxed for the duration of the build and
	# the exit code decides success.
	$previousPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"

	try {
		& $gradleWrapper -p $WorktreePath @buildTasks --no-daemon --console=plain 2>&1 |
				ForEach-Object {
					if ($_ -is [System.Management.Automation.ErrorRecord]) {
						# ToString() falls back to the exception type name when a
						# stderr line carries no message (a blank line, typically).
						$line = [string]$_.Exception.Message
					} else {
						$line = [string]$_
					}

					Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
					Write-Host "[$BranchName] $line"
				}
	} finally {
		$ErrorActionPreference = $previousPreference
	}

	$exitCode = $LASTEXITCODE
	if ($exitCode -ne 0) {
		throw "Gradle build failed for $BranchName (exit code $exitCode). Log: $LogPath"
	}

	$propertiesPath = Join-Path $WorktreePath "gradle.properties"
	$properties = Get-Content -LiteralPath $propertiesPath

	$mcLine = $properties | Where-Object { $_ -like "minecraft_version=*" } | Select-Object -First 1
	$modLine = $properties | Where-Object { $_ -like "mod_version=*" } | Select-Object -First 1

	if ($null -eq $mcLine -or $null -eq $modLine) {
		throw "Could not read minecraft_version/mod_version from $propertiesPath"
	}

	$minecraftVersion = $mcLine.Split("=", 2)[1]
	$modVersion = $modLine.Split("=", 2)[1]
	$commit = (& git -C $WorktreePath rev-parse HEAD).Trim()

	$results = @()

	foreach ($loader in $loaders) {
		$libraryPath = Join-Path $WorktreePath "$loader/build/libs"

		if (-not (Test-Path -LiteralPath $libraryPath -PathType Container)) {
			throw "Could not find artifact directory '$libraryPath' for $BranchName/$loader. Log: $LogPath"
		}

		$artifacts = @(Get-ChildItem -LiteralPath $libraryPath -File -Filter "tritium-music-$loader-*.jar" |
				Where-Object { $_.Name -notlike "*-sources.jar" })

		if ($artifacts.Count -ne 1) {
			throw "Expected one $loader artifact for $BranchName, found $($artifacts.Count). Log: $LogPath"
		}

		$artifact = $artifacts[0]

		$results += [pscustomobject]@{
			branch = $BranchName
			commit = $commit
			minecraftVersion = $minecraftVersion
			modVersion = $modVersion
			loader = $loader
			file = $artifact.Name
			source = $artifact.FullName
			log = $LogPath
			durationSeconds = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
		}
	}

	Write-Host "[$BranchName] Build completed"
	return $results
}

function Get-BuildJobFailureText {
	param([System.Management.Automation.Job]$Job)

	$messages = New-Object System.Collections.Generic.List[string]

	foreach ($childJob in @($Job.ChildJobs)) {
		if ($null -eq $childJob) {
			continue
		}

		foreach ($errorRecord in @($childJob.Error)) {
			if ($null -ne $errorRecord -and -not [string]::IsNullOrWhiteSpace($errorRecord.ToString())) {
				$messages.Add($errorRecord.ToString().Trim())
			}
		}

		# JobStateInfo.Reason is a RemoteException, which has no .Exception
		# property; read the message off the exception itself.
		$reason = $childJob.JobStateInfo.Reason
		if ($null -ne $reason) {
			$reasonText = [string]$reason.Message
			if ([string]::IsNullOrWhiteSpace($reasonText)) {
				$reasonText = $reason.ToString()
			}
			if (-not [string]::IsNullOrWhiteSpace($reasonText)) {
				$messages.Add($reasonText.Trim())
			}
		}
	}

	$receivedErrors = $null
	try {
		$null = Receive-Job -Job $Job -ErrorAction SilentlyContinue -ErrorVariable receivedErrors
	} catch {
		$messages.Add($_.Exception.Message)
	}

	foreach ($errorRecord in @($receivedErrors)) {
		if ($null -ne $errorRecord -and -not [string]::IsNullOrWhiteSpace($errorRecord.ToString())) {
			$messages.Add($errorRecord.ToString().Trim())
		}
	}

	$uniqueMessages = @($messages |
			Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
			Select-Object -Unique)

	if ($uniqueMessages.Count -eq 0) {
		$uniqueMessages = @("The background PowerShell job failed without exposing an exception message.")
	}

	return ($uniqueMessages -join [Environment]::NewLine)
}

function Complete-BuildJobs {
	foreach ($job in @($jobs)) {
		if ($null -ne $job.PSObject.Properties["Collected"]) {
			continue
		}

		if ($job.State -notin @("Completed", "Failed")) {
			continue
		}

		$job | Add-Member -NotePropertyName Collected -NotePropertyValue $true

		$branchName = $branchNames[$job.Name]
		$branchLogPath = $logPaths[$job.Name]

		if ($job.State -eq "Completed") {
			# Gradle's stderr lines have already been written to the branch log by
			# the job itself, so nothing else has to be preserved from the error
			# stream; it must simply not terminate this script.
			$results = @(Receive-Job -Job $job -ErrorAction SilentlyContinue)

			Write-Host "[$branchName] finished." -ForegroundColor Green
			$script:manifest += $results
		} else {
			$errorText = Get-BuildJobFailureText -Job $job
			$errorLogPath = Join-Path $jobLogRoot "$($branchName -replace '\.', '_').error.log"

			try {
				Set-Content -LiteralPath $errorLogPath -Value $errorText -Encoding UTF8
			} catch {
				# Preserving the failure text must not hide the failure itself.
			}

			Write-Host "[$branchName] FAILED:" -ForegroundColor Red
			Write-Host $errorText -ForegroundColor Red
			Write-Host "Log: $branchLogPath" -ForegroundColor Yellow
			Write-Host "Error log: $errorLogPath" -ForegroundColor Yellow
			$script:failedBranches += $branchName
		}
	}
}

try {
	New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
	$jobLogRoot = Join-Path $temporaryRoot "logs"
	New-Item -ItemType Directory -Path $jobLogRoot -Force | Out-Null

	# Validate all branches and create all worktrees BEFORE starting any build.
	# This keeps Git operations out of the parallel build phase.
	foreach ($branch in $branches) {
		& git -C $repositoryRoot show-ref --verify --quiet "refs/heads/$($branch.Name)"
		if ($LASTEXITCODE -ne 0) {
			throw "Local branch not found: $($branch.Name)"
		}

		$worktreePath = Join-Path $temporaryRoot $branch.Directory
		Write-Host "Creating worktree for $($branch.Name)..."

		& git -C $repositoryRoot worktree add --detach $worktreePath $branch.Name
		if ($LASTEXITCODE -ne 0) {
			throw "Unable to create worktree for $($branch.Name)"
		}

		$createdWorktrees += $worktreePath
	}

	Write-Host ""
	Write-Host "Starting parallel builds (MaxParallel = $MaxParallel)" -ForegroundColor Cyan
	Write-Host ""

	$jobs = @()
	$logPaths = @{}
	$branchNames = @{}

	# Start up to MaxParallel jobs at once. Start-Job is used instead of
	# ForEach-Object -Parallel so this script also works on Windows PowerShell 5.1.
	foreach ($branch in $branches) {
		while (@($jobs | Where-Object { $_.State -in @("NotStarted", "Running") }).Count -ge $MaxParallel) {
			Start-Sleep -Milliseconds 250
		}

		$worktreePath = Join-Path $temporaryRoot $branch.Directory
		$logPath = Join-Path $jobLogRoot "$($branch.Directory).log"
		$jobName = "build-$($branch.Directory)"

		Write-Host "Queueing $($branch.Name)..."

		$logPaths[$jobName] = $logPath
		$branchNames[$jobName] = $branch.Name

		$jobs += Start-Job -Name $jobName -ScriptBlock $buildJob -ArgumentList @(
			$branch.Name,
			$worktreePath,
			$javaHome,
			$logPath
		)
	}

	# Wait for all jobs and collect their results.
	$manifest = @()
	$failedBranches = @()

	while (@($jobs | Where-Object { $_.State -in @("NotStarted", "Running") }).Count -gt 0) {
		Complete-BuildJobs
		Start-Sleep -Milliseconds 250
	}

	# Jobs can change state between two polls, so sweep once more.
	Complete-BuildJobs

	# Do not copy anything if one of the branches failed.
	if ($failedBranches.Count -gt 0) {
		$logs = Get-ChildItem -LiteralPath $jobLogRoot -File -ErrorAction SilentlyContinue |
				ForEach-Object { $_.FullName }

		Write-Host ""
		Write-Host "One or more builds failed: $($failedBranches -join ', ')" -ForegroundColor Red
		Write-Host "Logs have been preserved at: $jobLogRoot" -ForegroundColor Yellow
		throw "One or more builds failed. See the preserved logs above."
	}

	# Stale artifacts are removed only now: a failed build must not wipe the
	# artifacts of the previous successful run.
	Get-ChildItem -LiteralPath $outputPath -File -ErrorAction SilentlyContinue |
			Where-Object {
				$_.Name -like "tritium-music-fabric-*.jar" -or
						$_.Name -like "tritium-music-neoforge-*.jar" -or
						$_.Name -like "tritium-music-forge-*.jar" -or
						$_.Name -eq "manifest.json"
			} |
			Remove-Item -Force

	# Copy all artifacts only after every branch has successfully built.
	foreach ($item in $manifest) {
		$destination = Join-Path $outputPath $item.file
		Copy-Item -LiteralPath $item.source -Destination $destination -Force

		$item | Add-Member -NotePropertyName sha256 -NotePropertyValue (
		(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
		)

		# source/log are temporary paths and should not leak into the final manifest.
		$item.PSObject.Properties.Remove("source")
		$item.PSObject.Properties.Remove("log")

		# Background jobs add remoting metadata to every returned object.
		$item.PSObject.Properties.Remove("PSComputerName")
		$item.PSObject.Properties.Remove("RunspaceId")
		$item.PSObject.Properties.Remove("PSShowComputerName")
	}

	# Keep manifest ordering stable regardless of which job finishes first.
	$manifest = @($manifest | Sort-Object branch, loader)

	$manifest | ConvertTo-Json -Depth 3 |
			Set-Content -LiteralPath (Join-Path $outputPath "manifest.json") -Encoding UTF8

	Write-Host ""
	Write-Host "All $($branches.Count) branches built successfully." -ForegroundColor Green
	Write-Host "Artifacts written to $outputPath"
}
finally {
	# Stop/remove background jobs first.
	if ($null -ne $jobs) {
		foreach ($job in @($jobs)) {
			try {
				if ($job.State -in @("Running", "NotStarted")) {
					Stop-Job -Job $job -ErrorAction SilentlyContinue
				}
				Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
			} catch {
				# Cleanup must continue.
			}
		}
	}

	# IMPORTANT:
	# On failure, keep the entire temporary directory so the per-branch logs
	# can be inspected. On success, remove worktrees and logs automatically.
	$buildSucceeded = ($null -ne $failedBranches -and $failedBranches.Count -eq 0 -and $null -ne $manifest -and $manifest.Count -gt 0)

	if ($buildSucceeded) {
		foreach ($worktreePath in $createdWorktrees) {
			if (Test-Path -LiteralPath $worktreePath) {
				$resolvedWorktree = [System.IO.Path]::GetFullPath($worktreePath)
				$resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)

				if (-not $resolvedWorktree.StartsWith(
						$resolvedTemporaryRoot + [System.IO.Path]::DirectorySeparatorChar,
						[System.StringComparison]::OrdinalIgnoreCase
				)) {
					throw "Unexpected worktree cleanup path: $resolvedWorktree"
				}

				Remove-Item -LiteralPath ("\\?\" + $resolvedWorktree) -Recurse -Force
			}
		}

		& git -C $repositoryRoot worktree prune

		if (Test-Path -LiteralPath $temporaryRoot) {
			Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
		}
	} else {
		Write-Host ""
		Write-Host "Build failed. Temporary files and logs have been preserved." -ForegroundColor Yellow
		Write-Host "Temporary root: $temporaryRoot" -ForegroundColor Yellow
		Write-Host "Logs:           $(Join-Path $temporaryRoot 'logs')" -ForegroundColor Yellow
		Write-Host ""
		Write-Host "After inspecting the logs, remove the temporary worktrees with:" -ForegroundColor DarkYellow

		foreach ($worktreePath in $createdWorktrees) {
			Write-Host "  git -C `"$repositoryRoot`" worktree remove --force `"$worktreePath`"" -ForegroundColor DarkYellow
		}

		Write-Host "  git -C `"$repositoryRoot`" worktree prune" -ForegroundColor DarkYellow
		Write-Host "Then delete $temporaryRoot." -ForegroundColor DarkYellow
	}
}