Short term todo list

 - Refactor de MessageDispatcher para permitir notifacion async de respuesta -- Done!
 - permitir cancel de future de MessageDispatcher -- Done!
 - refactorear los pedidos de procesamiento por RMI -- Done!
   - obtener un future
   - mandar mensajes a todos incluido yo
   - esperar respuestas
 - balancear el primario del add tambien
   - fucking hate that
 - futureimpl is not thread safe -- Done!
 - bancarse procesamiento en sigle node mode -- Done!
 - stop de processor -- Done!

Reglas para modo degradado:

 - Suicidar y re encolar pedidos de procesamiento en curso
   - Incluye matar pedidos remotos
 - bloquear nuevos pedidos

Cambio de topologia:

 - entrar en modo degradado
 - 2 fases de pasaje -- primarios y backups
   - elegir señales a pasar
     - al azar si es un add
     - las del nodo caido si es un drop
   - enviar y actualizar data de quien tiene la otra copia (incluye mandar mensajes si es necesario)
   - esperar a que me confirmen todos los sends
 - esperar confirmacion de todos para volver a modo normal

Cosas random

 - protocol FIFO pa orden de mensajes <= util para el add de una primary en otro nodo (bloque el add del otro lado, y no antiende el pedido de procesamiento hasta que ya esta agregada la señal)
 - mandarte mensaje de pedido de procesamiento a vos mismo -- Done!
 - simplemente dejar de responder cosas para el clean exit
