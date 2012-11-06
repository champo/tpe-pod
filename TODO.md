Short term todo list

 - Refactor de MessageDispatcher para permitir notifacion async de respuesta -- Done!
 - permitir cancel de future de MessageDispatcher -- Done!
 - refactorear los pedidos de procesamiento por RMI
   - obtener un future
   - del otro lado una queue de procesamiento que deriva a interfaces de procesamiento local y remotas (que entregan NotifiableFutures)
   - rearmar el sistema de procesamiento local para que sea async
 - balancear el primario del add tambien
   - fucking hate that

Reglas para modo degradado:

 - Suicidar y re encolar pedidos de procesamiento en curso
   - Incluye matar pedidos remotos
 - bloquear nuevos pedidos hasta volver a modo normal

Agregado de nodos:

 - entrar en modo degradado (asesinar todo? -- future optimization)
 - elegir n/(k-1) primarias y backups, pasarlo al nuevo nodo
 - broadcast de stable una vez que pase todo y recibi acks
 - retorno a modo normal cuando recibi todos los broadcasts (usar FLUSH para asegurar que todos sepan bien que estan modo normal)
