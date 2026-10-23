Chat con JGroups y mapa replicado

Este proyecto extiende un chat de consola basado en JGroups de chat and replicated-map. Los mensajes normales siguen funcionando y, además, se pueden guardar valores con el comando /store.

Cada nodo mantiene una copia local del mapa y un archivo WAL propio. Cuando recibe una operación de almacenamiento, primero la guarda en el WAL y luego actualiza el mapa. Al iniciar, reproduce su WAL para reconstruir el estado local antes de unirse al grupo.

Arquitectura
Cada instancia de la aplicación es un nodo JGroups. Los nodos se unen al mismo grupo y JGroups se encarga de la comunicación entre ellos y de notificar los cambios en la membresía.

flowchart LR
    subgraph N1["Nodo Virginia"]
        C1["Chat y comandos"]
        M1["HashMap local"]
        W1[("Virginia.wal")]
        C1 --> M1
        M1 -. "operación persistida antes de actualizar" .-> W1
    end

    subgraph N2["Nodo London"]
        C2["Chat y comandos"]
        M2["HashMap local"]
        W2[("London.wal")]
        C2 --> M2
        M2 -. "operación persistida antes de actualizar" .-> W2
    end

    subgraph N3["Nodo Bogota"]
        C3["Chat y comandos"]
        M3["HashMap local"]
        W3[("Bogota.wal")]
        C3 --> M3
        M3 -. "operación persistida antes de actualizar" .-> W3
    end

    N1 <--> |"mensajes y operaciones /store"| N2
    N2 <--> |"mensajes y operaciones /store"| N3
    N1 <--> |"mensajes y operaciones /store"| N3

    N1 -. "transferencia de estado" .-> N3
El diagrama muestra tres nodos, aunque se pueden ejecutar más o menos instancias. La transferencia de estado permite que un nodo nuevo obtenga el estado actual del grupo al incorporarse.

<img width="1408" height="768" alt="Arquitectura" src="https://github.com/user-attachments/assets/ec4f7e15-bdb6-4d3f-b170-396312adc6da" />



Comandos
/store <clave> <valor>: guarda o actualiza un valor y replica la operación en el grupo.
/state: muestra el mapa local del nodo.
Cualquier otra línea se envía como mensaje de chat.
quit o exit: cierra la aplicación.
Cómo se replica una operación
Al ejecutar, por ejemplo:

/store color azul
el nodo crea una operación con un identificador único y la envía al grupo mediante JGroups. Cada nodo que la recibe:

Comprueba si ya procesó ese identificador.
Si es una operación nueva, la escribe en su WAL.
Fuerza la escritura a disco antes de actualizar el mapa.
Actualiza su mapa local y registra el identificador como procesado.
El identificador, junto con el registro de operaciones procesadas, permite ignorar entregas duplicadas.

Write-Ahead Log
Cada nodo tiene su propio archivo WAL. Por ejemplo:

Virginia.wal
London.wal
Bogota.wal
El WAL guarda las operaciones de almacenamiento recibidas por el nodo. La operación se escribe antes de modificar el mapa en memoria. Al iniciar, la aplicación lee el archivo y reproduce las operaciones para reconstruir el mapa local.

El formato utilizado por el proyecto representa el identificador de la operación, la clave y el valor en cada registro. Si se modifica el formato, hay que asegurarse de que los valores que contengan separadores o saltos de línea puedan leerse correctamente.

Transferencia de estado
Cuando un nodo se incorpora al grupo, JGroups puede transferirle el estado actual. El nodo que proporciona el estado lo prepara mediante getState, y el nodo que se incorpora lo recibe mediante setState.

El WAL y la transferencia de estado resuelven necesidades distintas:

WAL: reconstruye el estado local que el nodo había guardado anteriormente.
Transferencia de estado: ayuda a que el nodo obtenga el estado actual del grupo al incorporarse.
Después de recibir el estado, el nodo participa en las operaciones nuevas del grupo como los demás miembros.

Requisitos
Java
Maven
JGroups 5.x
NetBeans u otro IDE, si se desea ejecutar las instancias desde el IDE
Compilar y ejecutar
Desde la carpeta del proyecto, compila con:

mvn clean package
Para ejecutar varios nodos desde NetBeans:

Abre el proyecto.
Configura el nombre del nodo en los argumentos de ejecución, por ejemplo Virginia.
Inicia la aplicación.
Repite el proceso en otras consolas usando nombres diferentes, como London y Bogota.
Cada nodo debe usar un nombre distinto para que sus archivos WAL también sean diferentes. Por ejemplo, Virginia utilizará Virginia.wal.

Experimentos
1. Operación normal con tres nodos
Inicia tres nodos con nombres distintos.
Envía mensajes de chat desde varias consolas.
Ejecuta /store desde más de un nodo.
Ejecuta /state en los tres nodos y compara los resultados.
2. Incorporación de un nodo y transferencia de estado
Inicia dos nodos.
Ejecuta varias operaciones /store.
Inicia un tercer nodo.
Comprueba que recibe el estado actual.
Ejecuta otra operación /store y verifica que los tres nodos la reciben.
3. Fallo de un nodo
Detén uno de los nodos mientras el grupo está en funcionamiento.
Revisa en las consolas restantes el cambio en la vista de membresía.
Continúa ejecutando operaciones /store en los nodos que siguen activos.
4. Recuperación desde el WAL
Vuelve a iniciar el nodo que detuviste, usando el mismo nombre.
Comprueba que reproduce su WAL local.
Verifica que se reincorpora al grupo y recibe el estado actual.
Ejecuta /state en los nodos y compara sus mapas.
Evidencias
Las evidencias de los experimentos están incluidas en el repositorio, en la carpeta:

docs/evidence/
Se incluyen capturas o registros de la operación normal, la incorporación de un nodo, el cambio de membresía tras detener un nodo y la recuperación desde el WAL.

Desarrollo asistido por IA
Usé Gemini como apoyo durante el desarrollo, especialmente para revisar la estructura de las operaciones de almacenamiento, el formato del WAL y el uso de las callbacks de transferencia de estado de JGroups.

Algunas consultas que hice fueron:

«¿Cómo puedo representar una operación de almacenamiento con un identificador único para enviarla por JGroups?»
«¿Cómo implementar un WAL sencillo en Java y reproducirlo al iniciar?»
«¿Qué función cumplen getState y setState en la transferencia de estado de JGroups?»
Después revisé las propuestas y las adapté al código del proyecto. Mantuve la solución pequeña: un mapa local por nodo, mensajes de JGroups y un archivo de texto para el WAL. No añadí frameworks ni servicios externos.

Preguntas de discusión
¿Por qué hay que escribir el WAL antes de actualizar el mapa?
El mapa está en memoria y se pierde si el proceso se cierra. Al guardar primero la operación en disco, el nodo puede recuperarla al reiniciar.

¿Qué ocurre si el nodo se cae después de escribir el WAL, pero antes de actualizar el mapa?
Al iniciar de nuevo, reproduce el WAL y aplica la operación al mapa. Así, la operación no se pierde aunque no alcanzara a actualizarse en memoria antes del fallo.

¿Qué ocurre si una operación se entrega más de una vez?
El nodo utiliza el identificador de la operación para reconocer que ya la procesó e ignorar la copia repetida.

¿Qué limitación queda si se pierden todos los nodos y todos los archivos WAL?
Se pierde el estado almacenado. Sin una copia de respaldo externa, no hay forma de reconstruirlo.

¿Cómo ayuda la transferencia de estado a un nodo nuevo?
Le proporciona una copia del estado actual del grupo al incorporarse, sin que tenga que recibir todas las operaciones históricas desde el principio.
