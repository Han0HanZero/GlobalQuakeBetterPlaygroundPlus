$ErrorActionPreference = 'Continue'

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$m2 = Join-Path $env:USERPROFILE '.m2\repository'

# Build classpath
$classes = @(
    (Join-Path $projectDir 'GlobalQuakeAPI\target\classes'),
    (Join-Path $projectDir 'GlobalQuakeCore\target\classes'),
    (Join-Path $projectDir 'GlobalQuakeClient\target\classes'),
    (Join-Path $projectDir 'GlobalQuakeServer\target\classes'),
    (Join-Path $projectDir 'libs\edu\sc\seis\seisFile\2.1.0-SNAPSHOT\seisFile-2.1.0-SNAPSHOT.jar')
)

$jars = Get-ChildItem -Path $m2 -Filter '*.jar' -Recurse | ForEach-Object { $_.FullName }

$classpath = ($classes + $jars) -join ';'

# Start Java GUI process (no console window)
$proc = Start-Process -FilePath 'javaw' `
    -ArgumentList '-Dfile.encoding=UTF-8', '-cp', $classpath, 'globalquake.playground.GlobalQuakePlayground' `
    -WorkingDirectory $projectDir `
    -WindowStyle Normal `
    -PassThru

Write-Host "Started GlobalQuake Playground (PID: $($proc.Id))"
