param(
	[string]$OutputDirectory = (Join-Path $PSScriptRoot "artifacts")
)

$ErrorActionPreference = "Stop"
$branches = @(
	[pscustomobject]@{ Name = "main"; Directory = "m" },
	[pscustomobject]@{ Name = "1.20.1"; Directory = "a" },
	[pscustomobject]@{ Name = "1.21.11"; Directory = "b" },
	[pscustomobject]@{ Name = "26.2"; Directory = "c" }
)

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
	# Do not invoke java through PowerShell's stderr redirection. Java writes
	# its version information to stderr, and PowerShell may turn that into
	# an exception when $ErrorActionPreference = "Stop".
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
	# First use our persistent setting.
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

			# JAVA_TOOL_OPTIONS is allowed during the actual build, but some JVMs
			# print its value to stderr. With $ErrorActionPreference = "Stop",
			# PowerShell can treat that stderr output as an exception.
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

	# As a convenience, check the Java currently available on PATH.
	$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
	if ($null -ne $javaCommand) {
		try {
			$javaHome = Resolve-Java25Home (Split-Path -Parent (Split-Path -Parent $javaCommand.Source))
			$javaExe = Join-Path $javaHome "bin\java.exe"
			$versionOutput = & $javaExe -version 2>&1 | Out-String

			if ($versionOutput -match 'version "25') {
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

			# JAVA_TOOL_OPTIONS is allowed during the actual build, but some JVMs
			# print its value to stderr. With $ErrorActionPreference = "Stop",
			# PowerShell can treat that stderr output as an exception.
			$version = Get-JavaVersion $javaExe

			if ($version -ne 25) {
				throw "This is Java $version, not Java 25"
			}

			# Persist the normalized JDK root for future runs.
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

	# Put Java 25 first on PATH.
	$javaBin = Join-Path $javaHome "bin"
	$pathEntries = $env:Path -split ';' | Where-Object {
		$_ -and ($_ -ne $javaBin)
	}
	$env:Path = $javaBin + ';' + ($pathEntries -join ';')

	Write-Host "Using Java 25: $javaHome"
}

# Every branch in this build script is built with Java 25.
Set-Java25

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
Get-ChildItem -LiteralPath $outputPath -File -ErrorAction SilentlyContinue |
		Where-Object { $_.Name -like "tritium-music-fabric-*.jar" -or $_.Name -like "tritium-music-neoforge-*.jar" -or $_.Name -like "tritium-music-forge-*.jar" -or $_.Name -eq "manifest.json" } |
		Remove-Item -Force

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("tmb-" + [System.Guid]::NewGuid().ToString("N").Substring(0, 8))
$createdWorktrees = @()
$manifest = @()

try {
	New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

	foreach ($branch in $branches) {
		& git -C $repositoryRoot show-ref --verify --quiet "refs/heads/$($branch.Name)"
		if ($LASTEXITCODE -ne 0) {
			throw "Local branch not found: $($branch.Name)"
		}

		$worktreePath = Join-Path $temporaryRoot $branch.Directory
		Write-Host "Building branch $($branch.Name) with Java 25"

		& git -C $repositoryRoot worktree add --detach $worktreePath $branch.Name
		if ($LASTEXITCODE -ne 0) {
			throw "Unable to create worktree for $($branch.Name)"
		}
		$createdWorktrees += $worktreePath

		if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
			$gradleWrapper = Join-Path $worktreePath "gradlew.bat"
		} else {
			$gradleWrapper = Join-Path $worktreePath "gradlew"
		}

		# Only the 1.20.1 branch contains a Forge loader.
		$buildTasks = @(":fabric:build", ":neoforge:build")
		if ($branch.Name -eq "1.20.1") {
			$buildTasks += ":forge:build"
		}

		& $gradleWrapper -p $worktreePath @buildTasks --no-daemon
		if ($LASTEXITCODE -ne 0) {
			throw "Gradle build failed for $($branch.Name)"
		}

		$properties = Get-Content -LiteralPath (Join-Path $worktreePath "gradle.properties")
		$minecraftVersion = ($properties | Where-Object { $_ -like "minecraft_version=*" } | Select-Object -First 1).Split("=", 2)[1]
		$modVersion = ($properties | Where-Object { $_ -like "mod_version=*" } | Select-Object -First 1).Split("=", 2)[1]
		$commit = (& git -C $worktreePath rev-parse HEAD).Trim()

		# Forge exists only on the 1.20.1 branch.
		$loaders = @("fabric", "neoforge")
		if ($branch.Name -eq "1.20.1") {
			$loaders += "forge"
		}

		foreach ($loader in $loaders) {
			$libraryPath = Join-Path $worktreePath "$loader/build/libs"
			$artifacts = @(Get-ChildItem -LiteralPath $libraryPath -File -Filter "tritium-music-$loader-*.jar" |
					Where-Object { $_.Name -notlike "*-sources.jar" })

			if ($artifacts.Count -ne 1) {
				throw "Expected one $loader artifact for $($branch.Name), found $($artifacts.Count)"
			}

			$destination = Join-Path $outputPath $artifacts[0].Name
			Copy-Item -LiteralPath $artifacts[0].FullName -Destination $destination -Force
			$manifest += [pscustomobject]@{
				branch = $branch.Name
				commit = $commit
				minecraftVersion = $minecraftVersion
				modVersion = $modVersion
				loader = $loader
				file = $artifacts[0].Name
				sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
			}
		}
	}

	$manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $outputPath "manifest.json") -Encoding UTF8
	Write-Host "Artifacts written to $outputPath"
} finally {
	foreach ($worktreePath in $createdWorktrees) {
		if (Test-Path -LiteralPath $worktreePath) {
			$resolvedWorktree = [System.IO.Path]::GetFullPath($worktreePath)
			$resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
			if (-not $resolvedWorktree.StartsWith($resolvedTemporaryRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
				throw "Unexpected worktree cleanup path: $resolvedWorktree"
			}
			Remove-Item -LiteralPath ("\\?\" + $resolvedWorktree) -Recurse -Force
		}
	}
	& git -C $repositoryRoot worktree prune
	if (Test-Path -LiteralPath $temporaryRoot) {
		Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
	}
}