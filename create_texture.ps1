Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 16, 16
$graphics = [System.Drawing.Graphics]::FromImage($bmp)
$graphics.Clear([System.Drawing.Color]::Transparent)
$brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 0, 0))
$graphics.FillRectangle($brush, 0, 0, 16, 16)
$bmp.Save('src/main/resources/assets/examplemod/textures/item/red_diamond_sword_overlay.png', [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$graphics.Dispose()
Write-Host 'PNG created successfully'