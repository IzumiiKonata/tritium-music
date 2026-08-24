param(
	[string]$OutputDirectory = (Join-Path $PSScriptRoot "artifacts")
)

$ErrorActionPreference = "Stop"
$branches = @(
	[pscustomobject]@{ Name = "main"; Directory = "m" },
	[pscustomobject]@{ Name = "26.1.2"; Directory = "a" },
	[pscustomobject]@{ Name = "1.21.11"; Directory = "b" }
)
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
	Where-Object { $_.Name -like "tritium-music-fabric-*.jar" -or $_.Name -like "tritium-music-neoforge-*.jar" -or $_.Name -eq "manifest.json" } |
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
		Write-Host "Building branch $($branch.Name)"
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

		& $gradleWrapper -p $worktreePath :fabric:build :neoforge:build --no-daemon
		if ($LASTEXITCODE -ne 0) {
			throw "Gradle build failed for $($branch.Name)"
		}

		$properties = Get-Content -LiteralPath (Join-Path $worktreePath "gradle.properties")
		$minecraftVersion = ($properties | Where-Object { $_ -like "minecraft_version=*" } | Select-Object -First 1).Split("=", 2)[1]
		$modVersion = ($properties | Where-Object { $_ -like "mod_version=*" } | Select-Object -First 1).Split("=", 2)[1]
		$commit = (& git -C $worktreePath rev-parse HEAD).Trim()

		foreach ($loader in @("fabric", "neoforge")) {
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
		& git -C $repositoryRoot worktree remove --force $worktreePath
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
