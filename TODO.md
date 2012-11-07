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

 - Suicidar y re encolar pedidos de procesamiento en curso -- Done!
   - Incluye matar pedidos remotos
 - bloquear nuevos pedidos -- Done!

Cambio de topologia:

 - entrar en modo degradado -- Done!
 - 2 fases de pasaje -- primarios y backups -- Done!
   - elegir señales a pasar -- Done!
   - enviar señal -- Done!
   - Guardar señales recibidas
   - actualizar data de quien tiene la otra copia
   - esperar a que me confirmen todos los sends -- Done!
   - esperar a que todos los nodos terminen la fase -- Done!
 - esperar confirmacion de todos para volver a modo normal -- Done!

Add:

 - hacer cambio de topologia -- Done!
 - elegir señales al azar

Remove:

 - hacer cambio de topologia
 - las del nodo caido si es un drop

Cosas random

 - protocol FIFO pa orden de mensajes <= util para el add de una primary en otro nodo (bloque el add del otro lado, y no antiende el pedido de procesamiento hasta que ya esta agregada la señal)
 - mandarte mensaje de pedido de procesamiento a vos mismo -- Done!
 - simplemente dejar de responder cosas para el clean exit
 - viewAccepted y join -- WTF un solo cambio o varios? <==== FATAL
