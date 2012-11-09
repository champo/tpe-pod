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
 - Abstraer la logica de seleccion de nodo a quien mandar en cambios de topologia (Se manda cagadas en el remove) -- Done!
 - clean exit
   - dejar de responder a pedidos externos
   - estado de "no acepto pedidos" en Processor
   - escuchar evento de "termine la cola de pedidos locales"
     - clear de señales
   - escuchar evento de "respondi a pedidos en curso"
     - aca puedo hacer el exit

Testing

 - drop de nodo durante procesamiento
 - con mas señales
 - varios cambios de topologia
 - clean exit

Reglas para modo degradado:

 - Suicidar y re encolar pedidos de procesamiento en curso -- Done!
   - Incluye matar pedidos remotos
 - bloquear nuevos pedidos -- Done!

Cambio de topologia:

 - entrar en modo degradado -- Done!
 - 2 fases de pasaje -- primarios y backups -- Done!
   - elegir señales a pasar -- Done!
   - enviar señal -- Done!
   - Guardar señales recibidas -- Done!
   - actualizar data de quien tiene la otra copia -- Done!
   - esperar a que me confirmen todos los sends -- Done!
   - esperar a que todos los nodos terminen la fase -- Done!
 - esperar confirmacion de todos para volver a modo normal -- Done!

Add:

 - disparar cambio de topologia -- Done!
 - elegir señales al azar -- Done!

Remove:

 - disparar cambio de topologia -- Done!
 - las del nodo caido si es un drop -- Done!

Cosas random

 - mandarte mensaje de pedido de procesamiento a vos mismo -- Done!
 - simplemente dejar de responder cosas para el clean exit
 - viewAccepted y join -- WTF un solo cambio o varios? <==== FATAL
