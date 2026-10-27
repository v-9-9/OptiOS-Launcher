# OptiOS Companion — Local Control Panel (no installs needed)
# Usage: right-click -> Run with PowerShell (must be on the same Wi-Fi as the projector)
# Enter the 6-digit code shown on the projector screen (Control Center panel).
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = 'OptiOS Companion'
$form.Size = New-Object System.Drawing.Size(420, 420)
$form.BackColor = [System.Drawing.Color]::FromArgb(14,17,22)
$form.ForeColor = [System.Drawing.Color]::White

$label = New-Object System.Windows.Forms.Label
$label.Text = 'Projector IP:'
$label.Location = New-Object System.Drawing.Point(15,15)
$label.Size = New-Object System.Drawing.Size(120,20)
$form.Controls.Add($label)

$ipBox = New-Object System.Windows.Forms.TextBox
$ipBox.Text = '192.168.100.92'
$ipBox.Location = New-Object System.Drawing.Point(140,12)
$ipBox.Width = 240
$form.Controls.Add($ipBox)

$codeLabel = New-Object System.Windows.Forms.Label
$codeLabel.Text = 'Code (rotates 60s):'
$codeLabel.Location = New-Object System.Drawing.Point(15,50)
$form.Controls.Add($codeLabel)

$codeBox = New-Object System.Windows.Forms.TextBox
$codeBox.Location = New-Object System.Drawing.Point(140,47)
$codeBox.Width = 240
$form.Controls.Add($codeBox)

$titleBox = New-Object System.Windows.Forms.TextBox
$titleBox.Text = 'Test notification'
$titleBox.Location = New-Object System.Drawing.Point(15,90)
$titleBox.Width = 370
$form.Controls.Add($titleBox)

$bodyBox = New-Object System.Windows.Forms.TextBox
$bodyBox.Text = 'Hello from the laptop!'
$bodyBox.Location = New-Object System.Drawing.Point(15,120)
$bodyBox.Width = 370
$form.Controls.Add($bodyBox)

$post = {
    param($Path, $Data)
    try {
        $url = "http://$($ipBox.Text):8692/$Path"
        $r = Invoke-RestMethod -Uri $url -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($Data)) -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 5
        return ($r | ConvertTo-Json -Compress)
    } catch { return "ERROR: $($_.Exception.Message)" }
}

$sendBtn = New-Object System.Windows.Forms.Button
$sendBtn.Text = 'Send notification'
$sendBtn.Location = New-Object System.Drawing.Point(15,160)
$sendBtn.Width = 370
$sendBtn.Add_Click({
    $data = "code=$([uri]::EscapeDataString($codeBox.Text))&title=$([uri]::EscapeDataString($titleBox.Text))&body=$([uri]::EscapeDataString($bodyBox.Text))"
    $out.Text = (& $post 'api/notify' $data)
})
$form.Controls.Add($sendBtn)

$sleepBtn = New-Object System.Windows.Forms.Button
$sleepBtn.Text = 'Sleep'
$sleepBtn.Location = New-Object System.Drawing.Point(15,200)
$sleepBtn.Width = 180
$sleepBtn.Add_Click({
    $data = "code=$([uri]::EscapeDataString($codeBox.Text))&state=on"
    $out.Text = (& $post 'api/sleep' $data)
})
$form.Controls.Add($sleepBtn)

$wakeBtn = New-Object System.Windows.Forms.Button
$wakeBtn.Text = 'Wake'
$wakeBtn.Location = New-Object System.Drawing.Point(205,200)
$wakeBtn.Width = 180
$wakeBtn.Add_Click({
    $data = "code=$([uri]::EscapeDataString($codeBox.Text))&state=off"
    $out.Text = (& $post 'api/sleep' $data)
})
$form.Controls.Add($wakeBtn)

$statusBtn = New-Object System.Windows.Forms.Button
$statusBtn.Text = 'Status (temps/RAM)'
$statusBtn.Location = New-Object System.Drawing.Point(15,240)
$statusBtn.Width = 370
$statusBtn.Add_Click({
    try {
        $url = "http://$($ipBox.Text):8692/api/status?code=$([uri]::EscapeDataString($codeBox.Text))"
        $r = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 5
        $out.Text = "CPU $($r.cpu_temp)C | GPU $($r.gpu_temp)C | free RAM $($r.mem_avail_kb) KB | time $($r.time)"
    } catch { $out.Text = "ERROR: $($_.Exception.Message)" }
})
$form.Controls.Add($statusBtn)

$out = New-Object System.Windows.Forms.Label
$out.Location = New-Object System.Drawing.Point(15,280)
$out.Size = New-Object System.Drawing.Size(370,80)
$out.ForeColor = [System.Drawing.Color]::FromArgb(140,200,255)
$form.Controls.Add($out)

[void]$form.ShowDialog()
