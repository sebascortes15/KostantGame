## Resumen del Proyecto: Juego de Kostant en Grafos


Este repositorio contiene una aplicación interactiva desarrollada en **Java Swing** que simula el fascinante **Juego de Kostant en grafos de lazo simple**. Además de la implementación estándar, la aplicación incorpora una **versión modificada** que incluye vértices ``siempre felices'', ofreciendo una experiencia de juego extendida y visualmente enriquecedora.


### ¿Qué es el Juego de Kostant?


El Juego de Kostant es un modelo de dinámica de fichas en grafos, donde el estado de cada nodo (punto en el grafo) depende de la cantidad de "fichas" que posee y de la suma de fichas de sus nodos vecinos. Los nodos pueden estar en tres estados:


* **Triste (Sad):** Si sus fichas son menores que la mitad de la mitad de la suma de las fichas de sus vecinos. Estos nodos son los únicos que pueden ser "reflejados".


* **Feliz (Happy):** Si sus fichas son exactamente la mitad de la suma de las fichas de sus vecinos.


* **Emocionado (Excited):** Si sus fichas son mayores que la mitad de la suma de las fichas de sus vecinos.


La operación clave es la **reflexión**: solo los nodos tristes pueden reflejarse, actualizando sus fichas según la fórmula: `c_i_nuevo = -c_i_viejo + (suma de fichas de los vecinos)`. El juego converge cuando no quedan nodos tristes.


### Nodos ``Siempre Felices'' (Modificación)


Una característica única de esta aplicación es la introducción de nodos ``siempre felices'' (`a_n'`), que son:


* Conectados únicamente a su nodo base (`a_n`).


* Siempre inicializados con 1 ficha.


* Permanecen siempre en estado "Feliz" y no pueden ser reflejados.


* Se eliminan automáticamente si su nodo base es eliminado.


### Características Clave de la Aplicación


La interfaz gráfica permite:


* **Construcción de Grafos:** Añadir nodos (`a_n`) y aristas, incluyendo nodos ``siempre felices'' (`a_n'`).


* **Gestión del Grafo:** Eliminar nodos (normales y ``siempre felices'').


* **Inicialización del Juego:** Configurar el estado inicial con fichas.


* **Control de Juego:**

  * Realizar reflexiones individuales.

  * **Jugar Automático:** Ejecutar reflexiones automáticamente. Haz clic repetidamente en el botón "Jugar Automático" para **acelerar el proceso**.

  * **Detener Automático:** Detener la ejecución automática en cualquier momento.

  * Reiniciar la configuración del juego (manteniendo el grafo).

  * Reiniciar completamente la aplicación para un nuevo grafo.


Esta herramienta es ideal para visualizar y experimentar las dinámicas del Juego de Kostant, tanto en su versión clásica como con la interesante modificación de los nodos ``siempre felices''.
