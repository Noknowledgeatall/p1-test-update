$ErrorActionPreference = 'Stop'

[int]$port = if ($args.Count -gt 0) { [int]$args[0] } else { 8765 }
$root = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'

if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "APK niet gevonden: $apkPath"
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
$listener.Start()

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $stream.ReadTimeout = 1500
            $buffer = New-Object byte[] 4096
            $received = New-Object System.Collections.Generic.List[byte]
            while ($received.Count -lt 16384) {
                try {
                    $read = $stream.Read($buffer, 0, $buffer.Length)
                } catch {
                    break
                }
                if ($read -le 0) {
                    break
                }
                for ($i = 0; $i -lt $read; $i++) {
                    $received.Add($buffer[$i])
                }
                $text = [System.Text.Encoding]::ASCII.GetString($received.ToArray())
                if ($text.Contains("`r`n`r`n")) {
                    break
                }
            }
            $request = [System.Text.Encoding]::ASCII.GetString($received.ToArray())
            $requestLine = ($request -split "`r`n")[0]

            if ($requestLine -like 'GET /app-debug.apk *' -or $requestLine -like 'GET / *') {
                $bytes = [System.IO.File]::ReadAllBytes($apkPath)
                $headers = "HTTP/1.1 200 OK`r`nContent-Type: application/vnd.android.package-archive`r`nContent-Disposition: attachment; filename=""energy-optimizer-phase1.apk""`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
                $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headers)
                $stream.Write($headerBytes, 0, $headerBytes.Length)
                $stream.Write($bytes, 0, $bytes.Length)
            } else {
                $body = [System.Text.Encoding]::UTF8.GetBytes("Gebruik /app-debug.apk")
                $headers = "HTTP/1.1 404 Not Found`r`nContent-Type: text/plain; charset=utf-8`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
                $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headers)
                $stream.Write($headerBytes, 0, $headerBytes.Length)
                $stream.Write($body, 0, $body.Length)
            }
        } finally {
            $client.Close()
        }
    }
} finally {
    $listener.Stop()
}
