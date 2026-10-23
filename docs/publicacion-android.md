# Una versión, dos distribuciones y publicación independiente

El workflow **Android release** genera un APK de GitHub y un AAB de Google Play
desde el mismo commit, con el mismo `versionName`, `versionCode` y `applicationId`.
Permite publicarlos juntos, primero uno y después el otro, o preparar ambos sin publicar.
No ofrece una transacción atómica entre tiendas: una puede aceptar una entrega y la otra fallar.

**GitHub recibe `app-github-release.apk`; Play recibe `app-play-release.aab`.**
No se envía el APK de GitHub al endpoint de Play ni se instala directamente el AAB.
Play genera los APK que entrega a cada dispositivo a partir de ese bundle.

## Ruta habitual: un tag para ambas tiendas

Una vez configurado y verificado el acceso a ambas tiendas:

1. Incrementar `versionName` **y** `versionCode` en `app/build.gradle.kts`.
   El código debe superar todos los usados en Play, incluidos borradores y pruebas.
2. Actualizar `release-notes.txt` (primera línea con la versión) y
   `play-release-notes/<idioma>.txt` (máximo 500 caracteres por idioma).
3. Revisar y confirmar los cambios en `dev`, `main` o `master`.
4. Crear y enviar un tag nuevo `X.Y.Z-stable` o `X.Y.Z-beta`.
   No mover un tag existente ni reutilizar una versión publicada.
5. Revisar **Android release** en Actions, sus artefactos y los resultados de cada tienda.

Los pasos de commit, push, tag y publicación requieren su propia autorización.
Los nombres de versión de los ejemplos son marcadores, no versiones reservadas.

### Valores predeterminados al enviar un tag

| Tag | GitHub | Google Play |
| --- | --- | --- |
| `X.Y.Z-stable` | Release estable con APK | `production`, estado solicitado `completed` |
| `X.Y.Z-beta` | Prerelease con APK | `internal`, estado solicitado `completed` |

`completed` significa que se solicita distribución en ese canal, **no** que Google ya
aprobó la entrega ni que los usuarios ya la reciben. La revisión y la publicación
gestionada pueden retrasar la disponibilidad. Los tags beta no pueden ir a producción.

## Controlar cuál se publica primero o dejar la segunda para otro día

En **Actions → Android release → Run workflow**, usar:

| Opción | Uso |
| --- | --- |
| `tag` | Tag de origen, inmutable; obligatorio para publicar |
| `destinations` | `none`: sólo preparar; `github`, `play` o `both` |
| `publication_order` | `parallel`, `github-first` o `play-first` |
| `artifact_run_id` | ID de la ejecución **original que construyó el par** |
| `play_track` | `auto`, `internal`, `alpha`, `beta` o `production` |
| `play_status` | `draft` para retener; `completed` para solicitar distribución |
| `send_for_review` | Solicitar revisión o dejar los cambios sin enviar |
| `source_ref` | Rama/commit sólo para un build nuevo con `destinations=none` |
| `verify_play_access` | Sólo verificar autenticación y consultar Play; omite las demás opciones, builds y publicaciones |

La ejecución manual comienza con `destinations=none`, `play_status=draft` y sin envío
a revisión. Hay que seleccionar conscientemente la publicación deseada.
Si ya existe un par para ese SHA, el workflow lo reutiliza automáticamente en lugar
de reconstruirlo. Un reintento no genera binarios nuevos; si hay más de un origen
ambiguo, exige seleccionar el ID original explícitamente.

Para separar las publicaciones por horas o días:

1. Configurar la variable de repositorio `TAG_RELEASE_DESTINATIONS=none` **antes** de
   enviar el tag. Se construyen y conservan ambos binarios, pero ninguno se publica.
2. Copiar el ID de esa ejecución original desde la URL de Actions.
3. Ejecutar manualmente con `destinations=github` y ese `artifact_run_id`.
4. Más adelante repetir con `destinations=play`, **el mismo tag y el mismo ID original**.
   Seleccionar explícitamente canal, estado y envío a revisión.

Conservar el valor anterior de `TAG_RELEASE_DESTINATIONS`. Si el bloqueo es temporal,
restaurarlo **después de que `prepare` haya seleccionado `none`**, no inmediatamente
después del push: el job lee la variable al ejecutarse. Borrar la variable restaura
el valor predeterminado `both`. No dejar ese cambio temporal sin registrar.

Para publicar primero en pruebas de Play, se puede usar un tag **stable** retenido,
enviarlo manualmente a `internal` y después promover el mismo par a `production`.
No se vuelve a subir el AAB si Google ya conserva exactamente su hash y código.
Un tag beta no se transforma en stable: para ese cambio se prepara otra versión
numérica/código, evitando que el actualizador ignore una transición con el mismo nombre.

`github-first` y `play-first` secuencian las operaciones: la segunda no empieza si la
primera falla. **No esperan la aprobación humana de Google**. Para esperar antes de
GitHub, usar publicaciones manuales separadas después de comprobar Play Console.

### Desde PowerShell (solicita publicación, no es una comprobación)

Tras autorizar la entrega y sustituir los marcadores:

```powershell
gh workflow run release.yml --repo rayo-alcantar/RayoAI --ref dev `
  -f tag=X.Y.Z-stable -f artifact_run_id=RUN_ID_ORIGINAL `
  -f destinations=both -f publication_order=github-first `
  -f play_track=production -f play_status=completed -f send_for_review=true
```

El wrapper de Windows exige el par original y nunca publica un APK local:

```powershell
.\deploy.bat --tag X.Y.Z-stable --run-id RUN_ID_ORIGINAL --destinations both --order github-first --track production --status completed --send-for-review
```

Estos comandos **encolan** una ejecución. No dicen que la versión ya esté desplegada.
La antigua sintaxis `deploy.bat VERSION stable|beta` ya no publica.

## Configuración inicial de Google Play

No introducir claves ni contraseñas en chats, commits, notas, capturas o artefactos.

Si todavía no hay un proyecto de Google Cloud, abrir
[Crear proyecto](https://console.cloud.google.com/projectcreate), usar un nombre como
`RayoAI Releases` y conservar el **ID del proyecto** que genera Google. El ID no es
una clave API ni el número de proyecto. Si ya existe un proyecto adecuado, se puede
reutilizar; no es necesario crear uno nuevo por cada versión. Si la consola exige
facturación, verificar ese requisito antes de vincular una cuenta o aceptar cargos.
[Guía oficial para crear proyectos](https://docs.cloud.google.com/resource-manager/docs/creating-managing-projects).

1. Elegir el proyecto de Google Cloud y habilitar **Google Play Android Developer API**.
2. Crear una cuenta de servicio específica para despliegues.
3. Invitar su correo en **Play Console → Usuarios y permisos** y limitar el acceso a
   `com.rayoai`. Dar permisos de pruebas y, sólo cuando se autorice, de producción.
   No necesita administrar usuarios, compras ni suscripciones.
4. Confirmar que la app ya existe, tiene una carga inicial y los consentimientos,
   declaraciones y requisitos legales resueltos en Play Console.
5. Elegir una de las autenticaciones de abajo.
6. Crear los environments `release-play-testing` y `release-play-production` en GitHub.
   Para producción se recomienda exigir revisores y limitar las ramas/tags permitidos.
   El workflow **no configura ni supone** esas protecciones.
7. Probar el acceso, el build y una entrega interna autorizada antes de habilitar producción.

### Autenticación preferida: Workload Identity Federation

No conserva una clave privada de Google en GitHub. Configurar un proveedor OIDC para
GitHub y autorizar únicamente el repositorio y los environments de publicación de Play.
Usar los IDs numéricos de organización/repositorio en las condiciones de confianza;
no confiar en cualquier repositorio de GitHub. Permitir impersonar sólo la cuenta de
servicio elegida, con los permisos mínimos de Workload Identity correspondientes.

Variables en el repositorio o en cada environment de Play:

| Nombre | Valor |
| --- | --- |
| `GOOGLE_PLAY_WORKLOAD_IDENTITY_PROVIDER` | Nombre completo `projects/.../locations/global/workloadIdentityPools/.../providers/...` |
| `GOOGLE_PLAY_SERVICE_ACCOUNT` | Correo de la cuenta de servicio autorizada en Play |
| `GOOGLE_PLAY_PRODUCTION_ENABLED` | `true` **sólo después de autorizar producción** |

La acción oficial de Google genera un token temporal con el alcance exclusivo
`androidpublisher`. No escribe un archivo de credenciales en el workspace.

### Alternativa: clave JSON de cuenta de servicio

Si no se puede usar federación, guardar el JSON como secret
`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, preferiblemente en cada environment de Play.
No configurar el proveedor WIF en ese caso. La clave debe permanecer fuera del
repositorio local y rotarse/revocarse según la política de la cuenta.
La cuenta sigue necesitando permisos explícitos en Play Console.

### Recursos de esta integración

Inventario inicial verificado el 23 de octubre de 2026; comprobar el estado vivo
antes de una entrega. Estos identificadores son públicos, no credenciales:

| Recurso | Identificador |
| --- | --- |
| Repositorio fuente/workflows | `rayo-alcantar/RayoAI`, rama habitual `dev` |
| Repositorio de distribución GitHub | `rayo-alcantar/rayoai_for_android` |
| Proyecto Google Cloud | `rayoai-releases`, número `456094598505` |
| Cuenta de servicio Play | `rayoai-play-publisher@rayoai-releases.iam.gserviceaccount.com` |
| Pool / proveedor WIF | `rayoai-github` / `github-actions` |
| Proveedor completo | `projects/456094598505/locations/global/workloadIdentityPools/rayoai-github/providers/github-actions` |
| App y paquete | RayoAI / `com.rayoai` |

La cuenta de servicio no tiene clave JSON ni roles Owner/Editor del proyecto.
El principal autorizado a impersonarla está limitado al repositorio numérico
`1021675917`, mediante `roles/iam.workloadIdentityUser`. El proveedor comprueba además
el propietario numérico `52678193`, ambos environments de Play, las rutas de los dos
workflows y eventos `push`/`workflow_dispatch`. El issuer es
`https://token.actions.githubusercontent.com`, con audiencia predeterminada del proveedor.

En Play, lectura de la app y lanzamiento a segmentos de pruebas no autorizan producción.
El permiso de producción que presenta Google se llama **«Lanzar a producción, excluir
dispositivos y usar la firma de apps de Play»** y agrupa esas capacidades; concederlo
sólo para RayoAI y con autorización expresa. Nunca ampliar a todas las apps ni dar
administración de usuarios, finanzas o compras para resolver un error de despliegue.
La variable `GOOGLE_PLAY_PRODUCTION_ENABLED=true` es una segunda barrera, no un permiso
de Google: hacen falta ambas cosas. Las protecciones de los environments deben
comprobarse aparte; su mera existencia no implica revisores ni restricciones de ramas.

### Ajustes de tags (variables del repositorio)

| Variable | Por defecto | Alternativas |
| --- | --- | --- |
| `TAG_RELEASE_DESTINATIONS` | `both` | `none`, `github`, `play` |
| `TAG_RELEASE_ORDER` | `parallel` | `github-first`, `play-first` |
| `TAG_PLAY_TRACK` | `auto` | `internal`, `alpha`, `beta`, `production` |
| `TAG_PLAY_STATUS` | `completed` | `draft` |

Estas variables no cambian los valores seleccionados explícitamente en una ejecución
manual. Un tag que solicita Play sin acceso configurado falla en ese destino: no
simula éxito ni omite silenciosamente la tienda pendiente.

## Qué se conserva igual y qué debe seguir siendo diferente

| Aspecto | GitHub | Google Play |
| --- | --- | --- |
| Código fuente, versión y código de versión | Iguales | Iguales |
| Paquete Android | `com.rayoai` | `com.rayoai` |
| Artefacto | APK firmado | AAB firmado con clave de carga |
| Actualizaciones | Consulta releases del repositorio público | Distribución/actualización por Play Store |
| Donaciones y actualizador GitHub | Habilitados | Deshabilitados |
| Firma del APK instalado | Firma local de release | Google puede firmar con otra clave mediante Play App Signing |

Las diferencias de firma pueden impedir instalar una distribución encima de la otra.
No cambiar `applicationId`, clave de firma ni datos del usuario para forzar ese cambio.
Esta integración no instala versiones ni habilita un actualizador GitHub dentro de Play.

### Canales del actualizador instalado desde GitHub

- **Estable** sólo ofrece releases estables.
- **Estables y beta** ofrece la versión numérica más alta de ambos tipos; una estable
  posterior no deja atrapado al usuario en una beta antigua. En igualdad numérica se
  prefiere la estable, pero no se ofrece como actualización del mismo número instalado.
- La preferencia antigua `ALL` se interpreta como **Estables y beta**, sin perder el
  consentimiento previo ni mostrar una tercera opción duplicada.
- Se comprueban todas las páginas de releases, se omiten borradores, tags no válidos
  y assets sin un APK descargable. Un fallo en una página invalida la consulta entera:
  no se informa «no hay actualizaciones» usando resultados parciales.
- Cambiar de canal inicia una comprobación, salvo si ya consulta o descarga una
  actualización; no se interrumpe esa operación. La app también consulta al inicializar
  su ViewModel y conserva el botón de búsqueda manual; no se añadió un servicio de
  comprobación periódica en segundo plano.
- Los errores de red no se presentan como «estás actualizado» y la cancelación de
  una consulta no se convierte en un error de red.

Las pruebas JVM de estas decisiones no sustituyen una comprobación con TalkBack,
una descarga/instalación real ni la entrega de una versión nueva a los usuarios.

## Verificación y recuperación

- El empaquetado inspecciona **los binarios reales** con `aapt2` y `bundletool`, comprueba
  ambas firmas y que la firma APK coincide con la clave de carga AAB.
- El par contiene hashes, tag, SHA, paquete, versión, código y notas. Los publicadores
  verifican el build de origen, el par completo y sus hashes antes de usar credenciales.
- Las publicaciones ejecutan código del tag validado. La comprobación de acceso usa
  el commit del workflow elegido; los artefactos descargados nunca ejecutan scripts.
- El publicador de GitHub conserva su contrato público y no sobrescribe releases públicas.
- Play verifica hash/código ya cargados, preserva la versión actual al añadir un borrador
  y rechaza otros borradores, rollouts en curso o códigos anteriores.
- El commit Play usa `ERROR_IF_IN_REVIEW`: no cancela revisiones pendientes. No reintenta
  automáticamente operaciones de resultado incierto ni cambia la intención de revisión.
- Después del commit consulta otro edit temporal para verificar AAB/canal/estado aceptados.
  Eso **no acredita aprobación ni disponibilidad**. Revisar Play Console por separado.
- Si falla una tienda, conservar la publicación aceptada y repetir **sólo la pendiente**
  con el ID del build original. No mover tags, sobrescribir APK ni recompilar el código usado.
- Los artefactos se solicitan por 90 días (el repositorio puede imponer un límite menor).
  Si expiran, el workflow se detiene; no reconstruye silenciosamente un binario distinto.
  Descargar y custodiar el par original si se necesita una retención mayor.

### Lista operativa de una entrega completa

1. **Preflight:** confirmar árbol limpio, rama y SHA, versión/código compartidos y
   notas de ambos destinos. Consultar Play (incluidos bundles de pruebas/borradores)
   y GitHub; elegir un código superior y un tag nuevo. Revisar si Google ya tiene
   cambios en revisión o si otra persona está preparando una entrega.
2. **Preparar una sola vez:** enviar el tag, retenido si se desea inspección previa.
   Esperar a que `Build paired artifacts` termine correctamente. Este job ejecuta
   todos los tests JVM antes de construir `assembleGithubRelease bundlePlayRelease`.
   Registrar su URL/ID y descargar `android-release-set` para conservar el par original.
3. **Inspeccionar el par:** comprobar `release-set.json`, el SHA del tag, `com.rayoai`,
   versión, código, certificado y todos los hashes. Su estructura esperada es:

   ```text
   release-set.json
   github/app-github-release.apk
   github/release.json
   github/SHA256SUMS.txt
   github/release-notes.txt
   play/app-play-release.aab
   play/release-notes.json
   ```

4. **Publicar:** usar el tag y `artifact_run_id` originales, seleccionar destinos,
   orden y canal. Para probar primero la subida a Play sin iniciar producción, elegir
   `destinations=play`, `play_track=internal` y el estado autorizado. Una promoción
   posterior a producción conserva exactamente el AAB, sin recompilarlo.
5. **Verificar cada tienda:** registrar por separado aceptación API, revisión y
   disponibilidad. No repetir el workflow sólo porque la tienda tarda en actualizarse.
6. **Cerrar:** restaurar variables temporales, anotar resultados y cualquier paso
   pendiente. Si sólo una tienda aceptó, conservarla y reintentar sólo la pendiente.

Para seguir una ejecución y recuperar sus artefactos, sustituir los marcadores:

```powershell
gh run view RUN_ID --repo rayo-alcantar/RayoAI --json status,conclusion,jobs,url
gh run watch RUN_ID --repo rayo-alcantar/RayoAI --interval 15 --exit-status
gh run download RUN_ID --repo rayo-alcantar/RayoAI --name android-release-set --dir DIRECTORIO_NUEVO
```

Descargar a un directorio nuevo y verificar antes de usarlo; no mezclarlo con otro
par ni sobrescribir el build local anterior. Los comandos de consulta/descarga no
publican. No ejecutar los publicadores locales como una comprobación inocua.

El coordinador usa un único grupo `concurrency` y no cancela una publicación en curso.
La cola predeterminada de GitHub admite una ejecución pendiente: un tercer envío
puede reemplazar la pendiente. Evitar despachos duplicados; conservar el ID devuelto
y seguir esa ejecución, sin suponer que todos los comandos encolados llegarán a correr.
Esta exclusión no bloquea los cambios manuales de otras personas en Play Console.

### Prueba de recepción y del actualizador

| Capa | Evidencia mínima | Lo que todavía no demuestra |
| --- | --- | --- |
| Build | Tests JVM, firmas, identidad y hashes del par verificados; job exitoso | Aceptación de una tienda o funcionamiento en dispositivo |
| GitHub | Release pública no draft con tag/canal correctos; descargar APK, `release.json`, notas y sumas; comparar SHA256 con el par | Que una instalación anterior ya lo detectó o lo instaló |
| Play API | Bundle con código/hash originales y release del canal/estado solicitado, verificados después del commit | Aprobación, rollout visible o actualización recibida |
| Play Console | Estado de revisión y publicación; revisar publicación gestionada y alcance del canal | Recepción en todos los dispositivos de inmediato |
| Dispositivo | Desde una versión anterior, comprobar detección, descarga e instalación autorizada en cada distribución | Accesibilidad completa sin una prueba TalkBack aparte |

Para consultar GitHub sin publicar:

```powershell
gh release view X.Y.Z-stable --repo rayo-alcantar/rayoai_for_android --json tagName,isDraft,isPrerelease,assets,url
gh api repos/rayo-alcantar/rayoai_for_android/releases/latest --jq .tag_name
```

En un dispositivo con la distribución GitHub anterior, buscar actualizaciones en
Ajustes y probar **Estable** y **Estables y beta**. Una release nueva debe tener un
número mayor que la instalada y un APK descargable; un tag/source push sin release
pública no basta. Confirmar descarga/instalación, conservación de datos y número
final sin forzar una reinstalación destructiva. La versión vieja conserva su lógica
vieja hasta instalar la nueva: las correcciones no cambian retroactivamente ese cliente.

En Play, usar una cuenta/dispositivo elegible para el canal y comprobar la actualización
desde Play Store. No esperar un aviso del actualizador GitHub: está deshabilitado en
esa variante. Para `internal`, verificar primero los testers y su acceso; publicar un
canal interno vacío no añade personas automáticamente. No cambiar firma/paquete ni
desinstalar datos del usuario para que pase una prueba.

Para la prueba de accesibilidad, comprobar por separado con TalkBack el selector de
canal, foco, botón de búsqueda y anuncios de búsqueda/error/actualización disponible.
Si no hay dispositivo o tester, registrar esa parte como pendiente, no como aprobada.

### Diagnóstico sin perder una entrega

| Síntoma | Comprobar / actuación segura |
| --- | --- |
| No aparece actualización GitHub | Versión instalada, canal, tag válido, release pública en el repositorio de distribución y APK presente; no confundir Actions terminado con release publicada |
| Error WIF o permiso `403` | Provider/SA del environment, IDs y claims exactos, API habilitada y permisos Play sólo para RayoAI; no abrir la confianza ni dar Owner/Editor |
| Producción bloqueada | Permiso Play de producción autorizado y `GOOGLE_PLAY_PRODUCTION_ENABLED=true` en `release-play-production`; el permiso de pruebas no basta |
| Código ya usado / hash distinto | Comparar manifiesto y bundle remoto; usar el original si coincide. Si difiere, detenerse y preparar una versión/código nuevos, sin reemplazar bytes |
| Otra revisión, borrador o rollout activo | Revisar con su responsable; no cancelar revisión, retirar rollout ni borrar borrador automáticamente |
| Timeout o commit de resultado incierto | Consultar estado remoto con una inspección separada antes de repetir escrituras; no asumir que falló la entrega |
| API aceptó pero no aparece en Play Store | Revisar Console, revisión, publicación gestionada, canal/testers y elegibilidad; no recompilar ni subir de nuevo por una demora |
| Artefacto ausente/expirado o más de un origen | Seleccionar el ID original válido. Si ya no está disponible, detenerse; no fabricar un reemplazo para el mismo código publicado |
| Sólo una tienda tuvo éxito | Reintentar exclusivamente la pendiente con el mismo tag y par; conservar la ya publicada |

### Comprobaciones sin publicación

Una vez enviados los workflows a GitHub y configurados los accesos, se puede probar
la federación **sin tag ni binarios** desde la rama que contiene esta integración:

```powershell
gh workflow run release.yml --repo rayo-alcantar/RayoAI --ref dev -f verify_play_access=true
```

Este modo ignora las entradas de publicación, no compila ni carga artefactos y usa
el environment `release-play-testing`. Abre un edit temporal, consulta bundles y
tracks y lo elimina, sin cambiar versiones ni hacer commit en Play. Comparte la
exclusión mutua del coordinador; no abrir otro edit manual con la misma cuenta de
servicio durante la comprobación. El resumen de Actions debe acreditar el acceso
real: la configuración de variables por sí sola no lo prueba.

La federación exige tanto `release.yml` como `publish-destination.yml`, los IDs
numéricos del repositorio y su propietario, los environments de Play y los eventos
`push` o `workflow_dispatch`. Renombrar esos workflows o environments requiere
actualizar la condición de confianza; no ampliarla a cualquier workflow para
hacer pasar una comprobación fallida.

Desde la raíz del repositorio:

```powershell
python -m unittest discover -s scripts/tests -v
.\gradlew.bat test
```

Las pruebas Python usan artefactos sintéticos y un servidor HTTP local. No prueban
credenciales reales, permisos de Play, aceptación del AAB ni disponibilidad en tiendas.
Un build local autorizado de ambas variantes usa:

```powershell
.\gradlew.bat assembleGithubRelease bundlePlayRelease
```

`python -m scripts.play_publish inspect` requiere un token temporal en
`GOOGLE_PLAY_ACCESS_TOKEN`: abre un edit temporal, lee tracks/bundles y lo elimina;
no sube binarios, no cambia tracks ni hace commit. Coordinarlo para no interferir
con otro edit de la misma cuenta de servicio.

### Rollback del código de automatización

Esta unidad abarca los dos workflows, `scripts/distribution.py`, `scripts/play_publish.py`,
sus pruebas, notas de Play, el wrapper y los cambios acotados de `scripts/release.py`.
Revertir esos archivos no revierte una entrega ya aceptada por una tienda: esas
operaciones requieren un plan separado. No borrar secretos, firmas o datos como rollback.

## Referencias

- [Acceso oficial a Google Play Developer API](https://developers.google.com/android-publisher/getting_started)
- [Subida de Android App Bundles](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload)
- [Edits y límites de la API](https://developers.google.com/android-publisher/edits)
- [Commit y protección de revisiones pendientes](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
- [Autenticación oficial para GitHub Actions](https://github.com/google-github-actions/auth)
- [Control de revisión y publicación](https://support.google.com/googleplay/android-developer/answer/9859654?hl=es)
- [Concurrencia y reemplazo de ejecuciones pendientes](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)
