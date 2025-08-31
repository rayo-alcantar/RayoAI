# rollback.ps1
# Rollback automático al último commit confirmado de la rama actual (o -Branch),
# descartando cambios no deseados y sin pedir hash.
# Flujo:
#   1) Captura HEAD actual como objetivo.
#   2) Si hay cambios, crea commit temporal (salvaguarda) y lo etiqueta.
#   3) Si la rama objetivo es la actual => reset --hard al objetivo.
#      Si no es la actual => branch -f a objetivo y checkout; luego reset --hard por higiene.
#   4) Borra el tag de salvaguarda; el commit temporal queda huérfano.
#   5) Opcional: -Push con --force-with-lease si origin/<rama> existe. -HardGC para limpiar ya.

param(
    [string] $Branch,
    [switch] $Push,
    [switch] $HardGC
)

$ErrorActionPreference = "Stop"

function Exec($cmd, [bool]$silent = $false) {
    if (-not $silent) { Write-Host "> $cmd" }
    & cmd /c $cmd
    if ($LASTEXITCODE -ne 0) { throw "Falló: $cmd" }
}

Write-Host "=== ROLLBACK (auto-HEAD, descarta cambios) ==="

# 0) Validaciones
try {
    Exec "git rev-parse --is-inside-work-tree" $true | Out-Null
} catch {
    Write-Host "ERROR: No estás en un repo Git."; exit 1
}

# Determinar rama objetivo
$current = ((& git rev-parse --abbrev-ref HEAD) | Out-String).Trim()
$targetBranch = if ($Branch) { $Branch } else { $current }

# Verificar rama local
& git show-ref --verify --quiet "refs/heads/$targetBranch"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: La rama local '$targetBranch' no existe."; exit 1
}

# Cambiar a la rama si hace falta
if ($current -ne $targetBranch) {
    Write-Host "Cambiando a '$targetBranch' ..."
    Exec "git checkout $targetBranch"
    $current = $targetBranch
}

# 1) Capturar commit objetivo (HEAD actual)
$targetCommit = ((& git rev-parse "HEAD^{commit}") | Out-String).Trim()
if (-not $targetCommit) { Write-Host "ERROR: No se pudo resolver HEAD."; exit 1 }
Write-Host "Commit objetivo: $targetCommit"

$fecha = Get-Date -Format "yyyyMMdd_HHmmss"
$tag = "rollback_safeguard_$fecha"

# 2) Salvaguarda (si hay cambios)
$statusLines = & git status --porcelain
$dirty = ( ($statusLines -join "`n").Trim() )

$tempCommit = $null
if ($dirty -ne "") {
    Write-Host "Cambios sin confirmar detectados. Creando commit temporal y tag..."
    Exec "git add -A"
    try {
        Exec "git commit -m ""WIP: salvaguarda previa a rollback $fecha"""
        $tempCommit = ((& git rev-parse HEAD) | Out-String).Trim()
        Exec "git tag $tag $tempCommit"
        Write-Host "Salvaguarda en $tempCommit (tag: $tag)."
    } catch {
        Write-Host "Aviso: no se creó commit temporal (posible falta de cambios efectivos)."
        $tempCommit = $null
        $headNow = ((& git rev-parse HEAD) | Out-String).Trim()
        Exec "git tag $tag $headNow"
    }
} else {
    Write-Host "No hay cambios sin confirmar. No se creará commit temporal."
}

# 3) Aplicar rollback
# Si estamos en la rama objetivo (worktree activa), NO usar 'branch -f'.
if ($current -eq $targetBranch) {
    Write-Host "Reset duro de '$targetBranch' a $targetCommit ..."
    Exec "git reset --hard $targetCommit"
} else {
    Write-Host "Reapuntando '$targetBranch' a $targetCommit ..."
    Exec "git branch -f $targetBranch $targetCommit"
    Exec "git checkout $targetBranch"
    Exec "git reset --hard $targetCommit"
}
Write-Host "Rollback aplicado en '$targetBranch'."

# 4) Limpiar salvaguarda
Write-Host "Eliminando tag de salvaguarda (si existe)..."
& git tag -d $tag | Out-Null

if ($HardGC) {
    Write-Host "Limpieza agresiva de reflogs y GC..."
    try {
        Exec "git reflog expire --expire=now --expire-unreachable=now --all"
        Exec "git gc --prune=now"
    } catch {
        Write-Host "Aviso: GC agresivo no crítico si falla."
    }
}

# 5) Push opcional solo si origin/<rama> existe
if ($Push) {
    $remoteHead = (& git ls-remote --heads origin $targetBranch)
    if ($LASTEXITCODE -eq 0 -and $remoteHead) {
        Write-Host "Empujando a origin/$targetBranch con --force-with-lease ..."
        Exec "git push origin $targetBranch --force-with-lease"
        Write-Host "Push completado."
    } else {
        Write-Host "Aviso: origin/$targetBranch no existe. No se realizó push."
        Write-Host "Si deseas crearla:  git push -u origin $targetBranch"
    }
}

Write-Host "Listo. Rama '$targetBranch' quedó en el último commit confirmado."
