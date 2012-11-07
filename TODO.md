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

Agregado de nodos:

 - entrar en modo degradado
 - elegir n/(k-1) primarias y backups, pasarlo al nuevo nodo
 - broadcast de stable una vez que pase todo y recibi acks
 - retorno a modo normal cuando recibi todos los broadcasts (usar FLUSH para asegurar que todos sepan bien que estan modo normal)
 - a la hora de agregar un primario/backup tengo que mirar si esta en la otra lista, y redirigirla si es asi

Cosas random

 - protocol FIFO pa orden de mensajes <= util para el add de una primary en otro nodo (bloque el add del otro lado, y no antiende el pedido de procesamiento hasta que ya esta agregada la señal)
 - mandarte mensaje de pedido de procesamiento a vos mismo -- Done!
 - simplemente dejar de responder cosas para el clean exit
