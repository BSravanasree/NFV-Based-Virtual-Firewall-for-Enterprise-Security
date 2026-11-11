# Verifies allowed/blocked traffic through the firewall VNF (Windows / PowerShell variant of verify-traffic.sh).
$C1 = 'nexus-lab-client1'; $C2 = 'nexus-lab-client2'; $SRV = '10.20.0.10'
$pass = 0; $fail = 0
function Check([string]$Name, [string]$Expected, [string]$Container, [string[]]$Cmd) {
    docker exec $Container @Cmd *> $null
    $got = if ($LASTEXITCODE -eq 0) { 'ALLOW' } else { 'BLOCK' }
    if ($got -eq $Expected) { Write-Host "[PASS] $Name -> $got"; $script:pass++ }
    else { Write-Host "[FAIL] $Name -> $got (expected $Expected)"; $script:fail++ }
}
Check 'client1 HTTP :80 to server'   'ALLOW' $C1 @('curl', '-sf', '-m', '3', "http://$SRV/")
Check 'client2 HTTP :80 to server'   'BLOCK' $C2 @('curl', '-sf', '-m', '3', "http://$SRV/")
Check 'client1 HTTP :8080 to server' 'BLOCK' $C1 @('curl', '-sf', '-m', '3', "http://${SRV}:8080/")
Check 'client1 ICMP to server'       'ALLOW' $C1 @('ping', '-c', '1', '-W', '2', $SRV)
Check 'client2 ICMP to server'       'ALLOW' $C2 @('ping', '-c', '1', '-W', '2', $SRV)
Check 'client1 TCP :22 to server'    'BLOCK' $C1 @('nc', '-z', '-w', '2', $SRV, '22')
Check 'client2 TCP :3306 to server'  'BLOCK' $C2 @('nc', '-z', '-w', '2', $SRV, '3306')
Write-Host "passed=$pass failed=$fail"
exit $fail
