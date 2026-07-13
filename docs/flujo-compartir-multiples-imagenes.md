# Compartir más de dos imágenes con RayoAI

## Alcance

Cuando la galería envía tres o más fotos mediante `ACTION_SEND_MULTIPLE`, RayoAI abre una pasarela de descripción. Una foto continúa usando el flujo previo. Dos fotos conservan el chat normal: se describe la primera y ambas quedan adjuntas al mismo chat para las preguntas posteriores.

## Flujo y capas

1. `presentation/SharingActivity` recibe las URIs desde `EXTRA_STREAM` y `ClipData`, dos variantes válidas de Android, y abre la aplicación sin esperar a copiar archivos.
2. `presentation/ui/navigation/AppNavigation` abre la pasarela solo si hay más de dos imágenes.
3. `MultiImagePassageViewModel` mantiene una cola estrictamente secuencial. Antes de cada solicitud importa la foto a `Pictures/RayoAI`, la recodifica y limita su lado mayor a 2048 px. Así la pantalla responde de inmediato, no depende de permisos efímeros de la galería y no manda originales descomunales a la red.
4. Tras importar cada elemento, invoca `DescribeImageUseCase`, persiste el resultado con `SaveCaptureUseCase` y continúa incluso si una foto falla.
5. Cada resultado exitoso crea una `CaptureEntity` individual: una URI, el mensaje de inicio y la respuesta. Por eso aparece en el historial y el botón «Abrir en chat» abre el chat habitual sin rutas especiales.

## Garantías de interacción

- Solo hay una solicitud al modelo activa; no se compite por memoria, red ni cuota.
- La pasarela aparece antes de la primera importación y anuncia «Preparando» y «Describiendo» a TalkBack.
- La pasarela informa el progreso y muestra la vista previa y la descripción disponible más reciente.
- «Foto anterior» y «Foto siguiente» solo navegan a fotos que ya terminaron correctamente.
- «Abrir en chat» permanece desactivado hasta que finaliza la cola completa.
- Un error de archivo o de Gemini queda aislado en su foto y puede reintentarse al terminar la cola; las demás no se pierden.
- Hay un límite defensivo de 50 elementos por Intent externo. No es un límite del historial: evita que una app externa agote recursos del dispositivo o la cuota de Gemini. Si un lote lo supera, la pasarela anuncia cuántas fotos no se agregaron; nunca se descartan en silencio.

## Consideración operativa

El procesamiento ocurre mientras la pasarela está abierta. Las copias locales sí quedan protegidas frente a la caducidad de permisos de la galería, pero no se ejecuta un Worker en segundo plano: si Android mata el proceso, se debe volver a compartir el lote. La siguiente evolución, si se requiere continuidad aun tras muerte de proceso, es persistir los estados de la cola en Room y delegar la ejecución a WorkManager.
