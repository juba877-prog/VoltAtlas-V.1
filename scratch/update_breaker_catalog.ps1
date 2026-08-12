$content = Get-Content 'C:\Users\mahmoud.mohamed\.gemini\antigravity\brain\57bc5bc1-6802-4a19-8a17-f4d66f383f54\.user_uploaded\media_1786195619574.txt' -Raw
$content = $content.Replace('"isdMinRatio: 1.0', '"isdMinRatio": 1.0')
if ($content.EndsWith('"isdMinRatio')) {
    $content += '": 1.0, "isdMaxRatio": 10.0, "iiFixed": 4000 }, "orderingCode": "1SDA100378R1" } ]'
} elseif (-not $content.Trim().EndsWith(']')) {
    $content += '": 1.0, "isdMaxRatio": 10.0, "iiFixed": 4000 }, "orderingCode": "1SDA100378R1" } ]'
}
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText('app/src/main/assets/breaker_catalog.json', $content, $utf8NoBom)
Write-Host "Updated breaker_catalog.json without BOM successfully."
