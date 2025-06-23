import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Clase para representar una arista dirigida con un peso
class Edge {
    public final String source;
    public final String target;
    public final int weight;

    public Edge(String source, String target, int weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        // Para la igualdad de aristas, consideramos origen, destino Y peso.
        // Esto es importante para almacenar aristas explícitas únicas.
        return weight == edge.weight &&
               Objects.equals(source, edge.source) &&
               Objects.equals(target, edge.target);
    }

    @Override
    public int hashCode() {
        // Un hash basado en origen, destino y peso.
        return Objects.hash(source, target, weight);
    }

    @Override
    public String toString() {
        return source + "->" + target + ":" + weight;
    }
}

class Graph {
    private final Map<String, Integer> nodesChips;
    // allDirectedEdges: Almacena TODAS las aristas dirigidas y sus pesos (explícitas e implícitas).
    // Se utiliza para los cálculos del juego (n_ij).
    // Clave: nodo origen, Valor: Mapa<nodo destino, peso>
    private final Map<String, Map<String, Integer>> allDirectedEdges;
    // explicitEdges: Almacena SÓLO las aristas dirigidas que fueron añadidas explícitamente por el usuario.
    // Se utiliza para la lógica de dibujo.
    private final Set<Edge> explicitEdges;
    private final Set<String> specialNodesLogicalNames;

    public Graph() {
        this.nodesChips = new ConcurrentHashMap<>();
        this.allDirectedEdges = new ConcurrentHashMap<>();
        this.explicitEdges = ConcurrentHashMap.newKeySet();
        this.specialNodesLogicalNames = ConcurrentHashMap.newKeySet();
    }

    public void addNode(String nodeName) {
        if (!nodesChips.containsKey(nodeName)) {
            nodesChips.put(nodeName, 0);
            allDirectedEdges.put(nodeName, new ConcurrentHashMap<>()); // Asegurarse de que el mapa de aristas salientes exista
            System.out.println("Nodo '" + nodeName + "' añadido al grafo lógico.");
        } else {
            System.out.println("Advertencia: El nodo '" + nodeName + "' ya existe en el grafo lógico.");
        }
    }

    public String addSpecialNode(String targetNodeLogicalName) {
        if (!nodesChips.containsKey(targetNodeLogicalName)) {
            throw new IllegalArgumentException("El nodo destino '" + targetNodeLogicalName + "' no existe en el grafo.");
        }
        
        String newSpecialNodeLogicalName = targetNodeLogicalName + "'"; 

        if (nodesChips.containsKey(newSpecialNodeLogicalName)) {
            throw new IllegalStateException("Ya existe un nodo especial asociado a '" + newSpecialNodeLogicalName + "'.");
        }
        
        specialNodesLogicalNames.add(newSpecialNodeLogicalName);
        nodesChips.put(newSpecialNodeLogicalName, 1); // Los nodos especiales comienzan con 1 ficha
        allDirectedEdges.put(newSpecialNodeLogicalName, new ConcurrentHashMap<>()); // Asegurarse de que el mapa de aristas salientes exista

        // Un nodo especial SIEMPRE tiene una arista DIRECTA y con peso 1 hacia su nodo base.
        // Esta arista es EXPLÍCITA y no debe activar la regla de arista inversa implícita.
        addDirectedEdgeInternal(newSpecialNodeLogicalName, targetNodeLogicalName, 1, true); 
        System.out.println("Nodo especial '" + newSpecialNodeLogicalName + "' añadido, conectado directamente a '" + targetNodeLogicalName + "'.");
        return newSpecialNodeLogicalName;
    }

    // Helper interno para añadir/actualizar cualquier arista dirigida (explícita o implícita)
    // Esto actualiza el mapa allDirectedEdges y, si es explícita, el conjunto explicitEdges.
    private void addDirectedEdgeInternal(String source, String target, int weight, boolean isExplicit) {
        if (!nodesChips.containsKey(source) || !nodesChips.containsKey(target)) {
            throw new IllegalArgumentException("Error: Ambos nodos (origen y destino) deben existir.");
        }
        if (source.equals(target)) {
            System.out.println("Advertencia: No se permiten bucles (aristas a sí mismo).");
            return;
        }

        // Añadir a allDirectedEdges (para cálculos de la lógica del juego)
        allDirectedEdges.computeIfAbsent(source, k -> new ConcurrentHashMap<>()).put(target, weight);

        // Gestionar aristas explícitas para el dibujo
        if (isExplicit) {
            // Eliminar cualquier arista explícita existente con el mismo origen/destino antes de añadir la nueva.
            // Esto asegura que si se añade una arista con el mismo origen/destino pero diferente peso, se actualice.
            explicitEdges.removeIf(e -> e.source.equals(source) && e.target.equals(target));
            explicitEdges.add(new Edge(source, target, weight));
        }
        // System.out.println("Arista dirigida interna procesada: " + source + "->" + target + ":" + weight + " (Explícita: " + isExplicit + ")");
    }


    // Este método es para aristas DIRIGIDAS añadidas por el usuario (desde el botón "Activar Modo Arista Dirigida")
    public void addDirectedEdge(String source, String target, int weight) {
        if (isSpecialNode(target)) {
             throw new IllegalArgumentException("No se pueden añadir aristas dirigidas hacia un nodo especial con este método. Los nodos especiales solo tienen una arista saliente hacia su nodo base.");
        }
        if (isSpecialNode(source) && !target.equals(getSpecialNodeConnectedTarget(source))) {
            throw new IllegalArgumentException("Un nodo especial solo puede tener una arista saliente hacia su nodo base.");
        }

        // 1. Añadir la arista explícita (Origen -> Destino con peso W)
        addDirectedEdgeInternal(source, target, weight, true);

        // 2. Gestionar la arista inversa implícita (Destino -> Origen con peso 1)
        // Comprobar si hay una arista *explícita* desde el destino hacia el origen
        boolean hasExplicitReverse = false;
        for (Edge e : explicitEdges) {
            if (e.source.equals(target) && e.target.equals(source)) {
                hasExplicitReverse = true;
                break;
            }
        }

        if (!hasExplicitReverse) {
            // Añadir/actualizar la arista inversa implícita (Destino -> Origen con peso 1)
            // Esta arista NO se añade a explicitEdges, por lo que no se dibujará.
            addDirectedEdgeInternal(target, source, 1, false);
            System.out.println("Arista dirigida '" + source + "' -> '" + target + "' con peso " + weight + " añadida. Inversa implícita (peso 1) también considerada.");
        } else {
             System.out.println("Arista dirigida '" + source + "' -> '" + target + "' con peso " + weight + " añadida. La inversa ya existe explícitamente.");
        }
    }

    // Este método es para aristas NO DIRIGIDAS (simples) añadidas por el usuario (desde el botón "Activar Modo Arista Simple")
    public void addUndirectedEdge(String node1, String node2) {
        if (isSpecialNode(node1) || isSpecialNode(node2)) {
            throw new IllegalArgumentException("No se pueden añadir aristas no dirigidas hacia/desde nodos especiales con este método.");
        }
        // Las aristas simples se definen como dos aristas dirigidas explícitas de peso 1.
        // Esto llamará a addDirectedEdge, que maneja la lógica interna incluyendo explicitEdges y allDirectedEdges.
        // Si se añade node1-node2, significa que n_2,1=1 y n_1,2=1. Ambas son *explícitas* y deben dibujarse.
        addDirectedEdge(node1, node2, 1);
        addDirectedEdge(node2, node1, 1);
        System.out.println("Arista no dirigida añadida entre '" + node1 + "' y '" + node2 + "'.");
    }

    public int getChips(String nodeName) {
        return nodesChips.getOrDefault(nodeName, 0);
    }

    public void setChips(String nodeName, int chips) {
        if (nodesChips.containsKey(nodeName)) {
            nodesChips.put(nodeName, chips);
        } else {
            System.out.println("Error: No se pueden establecer chips para un nodo inexistente: '" + nodeName + "'.");
        }
    }

    // Este método proporciona las aristas entrantes y sus pesos necesarios para los cálculos del juego (n_ij c_j).
    // Itera a través de allDirectedEdges para encontrar aristas donde 'nodeName' es el destino.
    public Map<String, Integer> getIncomingEdgesWithWeights(String nodeName) {
        Map<String, Integer> incoming = new ConcurrentHashMap<>();
        for (String sourceNode : allDirectedEdges.keySet()) {
            if (allDirectedEdges.get(sourceNode).containsKey(nodeName)) {
                incoming.put(sourceNode, allDirectedEdges.get(sourceNode).get(nodeName));
            }
        }
        return incoming;
    }

    // Devuelve el peso de una arista dirigida (desde allDirectedEdges, para la lógica del juego).
    public int getEdgeWeight(String source, String target) {
        return allDirectedEdges.getOrDefault(source, Collections.emptyMap()).getOrDefault(target, 0);
    }

    public List<String> getAllNodes() {
        return new ArrayList<>(nodesChips.keySet());
    }

    // Devuelve SÓLO las aristas EXPLÍCITAS para fines de dibujo.
    public Set<Edge> getExplicitEdges() {
        return Collections.unmodifiableSet(explicitEdges);
    }

    public void clear() {
        nodesChips.clear();
        allDirectedEdges.clear();
        explicitEdges.clear();
        specialNodesLogicalNames.clear();
        System.out.println("Grafo lógico limpiado.");
    }

    public boolean isSpecialNode(String nodeId) {
        return specialNodesLogicalNames.contains(nodeId);
    }

    public Set<String> getSpecialNodesLogicalNames() {
        return Collections.unmodifiableSet(specialNodesLogicalNames);
    }

    public String getSpecialNodeConnectedTarget(String specialNodeId) {
        if (!isSpecialNode(specialNodeId)) {
            return null;
        }
        // Un nodo especial solo tiene una arista saliente a su nodo base (que es explícita)
        Map<String, Integer> targets = allDirectedEdges.get(specialNodeId);
        if (targets != null && !targets.isEmpty()) {
            return targets.keySet().iterator().next();
        }
        return null;
    }

    public void removeNode(String nodeName) {
        if (!nodesChips.containsKey(nodeName)) {
            System.out.println("Advertencia: El nodo '" + nodeName + "' no existe para ser eliminado.");
            return;
        }

        // Si el nodo a eliminar no es especial, verificar si tiene un nodo especial asociado y eliminarlo
        if (!isSpecialNode(nodeName)) {
            String associatedSpecialNode = nodeName + "'";
            if (isSpecialNode(associatedSpecialNode)) {
                removeNode(associatedSpecialNode); // Llamada recursiva para eliminar el nodo especial
            }
        }
        
        // Eliminar todas las aristas salientes del nodo en allDirectedEdges
        allDirectedEdges.remove(nodeName); 
        // Eliminar las aristas explícitas relacionadas con este nodo (tanto origen como destino)
        explicitEdges.removeIf(e -> e.source.equals(nodeName) || e.target.equals(nodeName));

        // Eliminar las referencias al nodo en las listas de adyacencia de otros nodos (aristas entrantes)
        for (String otherNode : allDirectedEdges.keySet()) {
            allDirectedEdges.get(otherNode).remove(nodeName);
        }

        nodesChips.remove(nodeName);
        specialNodesLogicalNames.remove(nodeName);

        System.out.println("Nodo '" + nodeName + "' y sus aristas incidentes eliminados del grafo lógico.");
    }
}

class KostantGame {
    private final Graph graph;

    public KostantGame(Graph graph) {
        this.graph = graph;
    }

    // Calcula la suma ponderada de chips de los vecinos que tienen aristas entrantes al nodo
    private int getNeighborsSum(String node) {
        int sum = 0;
        // Iterar sobre los nodos fuente (vecinos) que tienen aristas dirigidas hacia 'node'
        for (Map.Entry<String, Integer> entry : graph.getIncomingEdgesWithWeights(node).entrySet()) {
            String sourceNode = entry.getKey();
            int weight = entry.getValue();
            sum += graph.getChips(sourceNode) * weight; // Suma (chips del vecino * peso de la arista)
        }
        return sum;
    }

    public String getNodeState(String node) {
        if (graph.isSpecialNode(node)) {
            return "Happy"; // Los nodos especiales son siempre felices
        }

        int c_i = graph.getChips(node);
        int sum_neighbors = getNeighborsSum(node);
        double threshold = 0.5 * sum_neighbors;

        if (c_i < threshold) {
            return "Sad"; // Triste
        } else if (c_i > threshold) {
            return "Excited"; // Emocionado
        } else {
            return "Happy"; // Feliz (en equilibrio)
        }
    }

    public void initializeGame(String specifiedInitialNodeLogicalName) {
        // Reiniciar todos los chips a 0
        for (String node : graph.getAllNodes()) {
            graph.setChips(node, 0);
        }

        // Si hay nodos especiales, inicializarlos con 1 chip. Deshabilita el modo estándar.
        if (!graph.getSpecialNodesLogicalNames().isEmpty()) {
            for (String specialNode : graph.getSpecialNodesLogicalNames()) {
                graph.setChips(specialNode, 1);
                System.out.println("Juego modificado inicializado. El nodo especial '" + specialNode + "' tiene 1 chip.");
            }
        } else { // Si no hay nodos especiales, inicializar con el nodo especificado.
            if (!graph.getAllNodes().contains(specifiedInitialNodeLogicalName)) {
                throw new IllegalArgumentException("El nodo inicial '" + specifiedInitialNodeLogicalName + "' no existe en el grafo.");
            }
            graph.setChips(specifiedInitialNodeLogicalName, 1);
            System.out.println("Juego estándar inicializado. El nodo '" + specifiedInitialNodeLogicalName + "' tiene 1 chip. Los demás tienen 0.");
        }
    }

    public List<String> getUnhappyNodes() {
        List<String> unhappyNodes = new ArrayList<>();
        for (String node : graph.getAllNodes()) {
            if (!graph.isSpecialNode(node) && getNodeState(node).equals("Sad")) {
                unhappyNodes.add(node);
            }
        }
        return unhappyNodes;
    }

    public int performReflection(String nodeToReflect) {
        if (!graph.getAllNodes().contains(nodeToReflect)) {
            throw new IllegalArgumentException("El nodo '" + nodeToReflect + "' no existe en el grafo.");
        }
        if (graph.isSpecialNode(nodeToReflect)) {
            throw new IllegalStateException("El nodo especial es siempre feliz y no puede ser reflejado.");
        }
        if (!getNodeState(nodeToReflect).equals("Sad")) {
            throw new IllegalStateException("El nodo '" + nodeToReflect + "' no está triste y no puede ser reflejado.");
        }

        int old_c_i = graph.getChips(nodeToReflect);
        int sum_neighbors_c_j = getNeighborsSum(nodeToReflect);
        
        // Fórmula de reflexión: c_i_nuevo = -c_i_viejo + sum(n_ij * c_j_vecinos)
        int new_c_i = -old_c_i + sum_neighbors_c_j;
        graph.setChips(nodeToReflect, new_c_i);
        System.out.println("Reflexión realizada en el nodo '" + nodeToReflect + "'. Los chips cambiaron de " + old_c_i + " a " + new_c_i + ".");
        return new_c_i;
    }

    public int playUntilConverged(int maxSteps) {
        int stepsTaken = 0;
        while (stepsTaken < maxSteps) {
            List<String> unhappyNodes = getUnhappyNodes();
            if (unhappyNodes.isEmpty()) {
                System.out.println("El juego ha convergido: No quedan nodos tristes.");
                break; // El juego ha terminado
            }

            // Estrategia simple: elegir el primer nodo triste para la reflexión.
            String nodeToReflect = unhappyNodes.get(0);
            try {
                performReflection(nodeToReflect);
            } catch (IllegalStateException e) {
                System.err.println("Error inesperado durante la reflexión automática: " + e.getMessage());
                break;
            }
            stepsTaken++;
        }

        if (stepsTaken >= maxSteps) {
            System.out.println("El juego se detuvo después de " + maxSteps + " pasos (límite alcanzado). Todavía pueden quedar nodos tristes.");
        }
        return stepsTaken;
    }
}

interface NodePlacementListener {
    void onNodePlaced(String nodeName);
    void onPlacementError(String message);
    void onNodePlacementCancelled(String message);
}

interface EdgeCreationListener {
    // isUndirected se usa para aristas simples (no dirigidas, peso 1 en ambas direcciones)
    // isDirected se usa para aristas dirigidas (con peso específico, en una dirección)
    void onEdgeCreated(String sourceNode, String targetNode, boolean isUndirected, boolean isDirected); 
    void onEdgeCreationCancelled(String message); 
    void onEdgeCreationError(String message);
}

interface NodeInteractionListener {
    void onNodeClicked(String nodeLogicalName);
}


class GraphPanel extends JPanel {
    private final Graph graph;
    private final KostantGame game;
    private final Map<String, Point> nodePositions;
    private final int NODE_SIZE = 60;
    private String pendingNodeName = null;
    private String firstNodeSelectedForEdge = null; // Nodo origen en modo arista
    private boolean isSimpleEdgeMode = false; // Modo para añadir aristas simples no dirigidas por clic
    private boolean isDirectedEdgeMode = false; // Nuevo: Modo para añadir aristas dirigidas por clic
    private int currentDirectedEdgeWeight = 1; // Nuevo: Peso para las aristas dirigidas en modo clic
    private boolean isNodeMode = false;
    private boolean isDeleteMode = false;

    private final NodePlacementListener nodePlacementListener;
    private final EdgeCreationListener edgeCreationListener;
    private final NodeInteractionListener nodeInteractionListener;


    public GraphPanel(Graph graph, KostantGame game, NodePlacementListener nodePlacementListener, EdgeCreationListener edgeCreationListener, NodeInteractionListener nodeInteractionListener) {
        this.graph = graph;
        this.game = game;
        this.nodePositions = new ConcurrentHashMap<>();
        this.nodePlacementListener = nodePlacementListener;
        this.edgeCreationListener = edgeCreationListener;
        this.nodeInteractionListener = nodeInteractionListener;
        setBackground(new Color(60, 60, 60)); 

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isNodeMode) {
                    int x = e.getX();
                    int y = e.getY();

                    for (Map.Entry<String, Point> entry : nodePositions.entrySet()) {
                        Point existingPos = entry.getValue();
                        double distance = existingPos.distance(x, y);
                        if (distance < NODE_SIZE * 0.9) { // Evitar superposición de nodos
                            nodePlacementListener.onPlacementError("Ya hay un nodo muy cerca de esa posición. Elige otro lugar.");
                            return;
                        }
                    }

                    graph.addNode(pendingNodeName);
                    
                    nodePositions.put(pendingNodeName, new Point(x, y));
                    repaint();
                    nodePlacementListener.onNodePlaced(pendingNodeName);
                } else if (isSimpleEdgeMode) { // Modo para añadir aristas simples (no dirigidas)
                    String clickedNode = getNodeAt(e.getPoint());

                    if (clickedNode == null) {
                        edgeCreationListener.onEdgeCreationCancelled("Clic fuera de un nodo. Conexión de aristas simples cancelada.");
                        resetSimpleEdgeMode();
                        return;
                    }
                    
                    // No se permite añadir aristas no dirigidas hacia/desde un nodo especial
                    if (graph.isSpecialNode(clickedNode)) {
                         edgeCreationListener.onEdgeCreationError("No se pueden añadir aristas no dirigidas hacia/desde un nodo especial.");
                         resetSimpleEdgeMode();
                         return;
                    }

                    if (firstNodeSelectedForEdge == null) {
                        firstNodeSelectedForEdge = clickedNode;
                        // Notificar que se ha seleccionado el nodo origen
                        edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, null, true, false); // True para no dirigida, false para dirigida
                        repaint();
                    } else {
                        if (clickedNode.equals(firstNodeSelectedForEdge)) {
                            edgeCreationListener.onEdgeCreationError("No puedes conectar un nodo consigo mismo.");
                            firstNodeSelectedForEdge = null;
                            repaint();
                        } else {
                            try {
                                // Llamada al nuevo método para añadir arista no dirigida
                                graph.addUndirectedEdge(firstNodeSelectedForEdge, clickedNode); 
                                edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, clickedNode, true, false); // True para no dirigida, false para dirigida
                                firstNodeSelectedForEdge = null;
                                repaint();
                            } catch (IllegalArgumentException ex) {
                                edgeCreationListener.onEdgeCreationError(ex.getMessage());
                                firstNodeSelectedForEdge = null;
                                repaint();
                            } catch (IllegalStateException ex) {
                                // Para el caso de nodo especial como origen
                                edgeCreationListener.onEdgeCreationError(ex.getMessage());
                                firstNodeSelectedForEdge = null;
                                repaint();
                            }
                        }
                    }
                } else if (isDirectedEdgeMode) { // Nuevo: Modo para añadir aristas dirigidas
                    String clickedNode = getNodeAt(e.getPoint());

                    if (clickedNode == null) {
                        edgeCreationListener.onEdgeCreationCancelled("Clic fuera de un nodo. Conexión de aristas dirigidas cancelada.");
                        resetDirectedEdgeMode();
                        return;
                    }

                    // No se permiten aristas dirigidas hacia nodos especiales con este método
                    if (graph.isSpecialNode(clickedNode)) {
                        edgeCreationListener.onEdgeCreationError("No se pueden añadir aristas dirigidas hacia un nodo especial con este método.");
                        resetDirectedEdgeMode();
                        return;
                    }

                    if (firstNodeSelectedForEdge == null) {
                        firstNodeSelectedForEdge = clickedNode;
                        edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, null, false, true); // False para no dirigida, true para dirigida
                        repaint();
                    } else {
                        if (clickedNode.equals(firstNodeSelectedForEdge)) {
                            edgeCreationListener.onEdgeCreationError("No puedes conectar un nodo consigo mismo.");
                            firstNodeSelectedForEdge = null;
                            repaint();
                        } else {
                            try {
                                graph.addDirectedEdge(firstNodeSelectedForEdge, clickedNode, currentDirectedEdgeWeight); // Cambiado a addDirectedEdge
                                edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, clickedNode, false, true); // False para no dirigida, true para dirigida
                                firstNodeSelectedForEdge = null;
                                repaint();
                            } catch (IllegalArgumentException ex) {
                                edgeCreationListener.onEdgeCreationError(ex.getMessage());
                                firstNodeSelectedForEdge = null;
                                repaint();
                            } catch (IllegalStateException ex) {
                                edgeCreationListener.onEdgeCreationError(ex.getMessage());
                                firstNodeSelectedForEdge = null;
                                repaint();
                            }
                        }
                    }
                }
                else if (isDeleteMode) {
                    String clickedNode = getNodeAt(e.getPoint());
                    if (clickedNode != null) {
                        nodeInteractionListener.onNodeClicked(clickedNode);
                    } else {
                        nodeInteractionListener.onNodeClicked(null);
                    }
                } else { // Modo normal, permite reflexión por clic si el juego está activo
                    String clickedNode = getNodeAt(e.getPoint());
                    if (clickedNode != null) {
                        nodeInteractionListener.onNodeClicked(clickedNode);
                    }
                }
            }
        });
    }

    private String getNodeAt(Point p) {
        for (Map.Entry<String, Point> entry : nodePositions.entrySet()) {
            Point nodeCenter = entry.getValue();
            double distance = nodeCenter.distance(p);
            if (distance <= NODE_SIZE / 2) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void setPendingNodeName(String nodeName) {
        this.pendingNodeName = nodeName;
    }

    public String getPendingNodeName() {
        return pendingNodeName;
    }

    public void setNodeMode(boolean active) {
        this.isNodeMode = active;
        this.pendingNodeName = null;
        repaint();
    }

    public boolean isNodeMode() {
        return isNodeMode;
    }

    public String getFirstNodeSelectedForEdge() {
        return firstNodeSelectedForEdge;
    }

    public void startSimpleEdgeMode() { // Nuevo método para modo arista simple
        this.isSimpleEdgeMode = true;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public void resetSimpleEdgeMode() { // Nuevo método para resetear modo arista simple
        this.isSimpleEdgeMode = false;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }
    
    public boolean isSimpleEdgeMode() { // Nuevo método para verificar modo arista simple
        return isSimpleEdgeMode;
    }

    // Nuevo: Métodos para el modo arista dirigida
    public void startDirectedEdgeMode(int weight) {
        this.isDirectedEdgeMode = true;
        this.currentDirectedEdgeWeight = weight;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public void resetDirectedEdgeMode() {
        this.isDirectedEdgeMode = false;
        this.currentDirectedEdgeWeight = 1; // Resetear a peso predeterminado
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public boolean isDirectedEdgeMode() {
        return isDirectedEdgeMode;
    }

    // Nuevo: Getter para currentDirectedEdgeWeight
    public int getCurrentDirectedEdgeWeight() {
        return currentDirectedEdgeWeight;
    }


    public void setDeleteMode(boolean active) {
        this.isDeleteMode = active;
        repaint();
    }

    public boolean isDeleteMode() {
        return isDeleteMode;
    }

    // Convierte un número a su subíndice Unicode
    public String convertToSubscript(String numberString) { 
        StringBuilder subscript = new StringBuilder();
        for (char c : numberString.toCharArray()) {
            switch (c) {
                case '0': subscript.append('₀'); break;
                case '1': subscript.append('₁'); break;
                case '2': subscript.append('₂'); break;
                case '3': subscript.append('₃'); break;
                case '4': subscript.append('₄'); break;
                case '5': subscript.append('₅'); break;
                case '6': subscript.append('₆'); break;
                case '7': subscript.append('₇'); break;
                case '8': subscript.append('₈'); break;
                case '9': subscript.append('₉'); break;
                default: subscript.append(c);
            }
        }
        return subscript.toString();
    }

    // Obtiene la representación de visualización de un nodo lógico (ej. "1" -> "α₁", "2'" -> "α₂'")
    public String getNodeDisplayString(String logicalNodeName) {
        if (logicalNodeName.endsWith("'")) {
            String baseNodeLogicalName = logicalNodeName.substring(0, logicalNodeName.length() - 1);
            try {
                String subscriptPart = convertToSubscript(baseNodeLogicalName);
                return "α" + subscriptPart + "'";
            }
            catch (NumberFormatException e) {
                return "α" + logicalNodeName + "'";
            }
        }
        try {
            String subscriptPart = convertToSubscript(logicalNodeName);
            return "α" + subscriptPart;
        }
        catch (NumberFormatException e) {
            return "α" + logicalNodeName;
        }
    }

    public void addNodePosition(String logicalNodeName, Point p) {
        nodePositions.put(logicalNodeName, p);
        repaint();
    }

    public void removeNodePosition(String logicalNodeName) {
        nodePositions.remove(logicalNodeName);
        repaint();
    }

    public Point getNodePosition(String logicalNodeName) {
        return nodePositions.get(logicalNodeName);
    }

    public int getNodeSize() {
        return NODE_SIZE;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g; 
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Conjunto para rastrear pares de nodos ya dibujados como aristas no dirigidas
        // Esto evita dibujar dos veces una arista A-B (una por A->B y otra por B->A)
        Set<String> drawnUndirectedPairs = new HashSet<>();

        // Dibujar solo aristas explícitas
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(2));
        for (Edge edge : graph.getExplicitEdges()) { // Iterar SÓLO sobre aristas explícitas
            Point p1 = nodePositions.get(edge.source);
            Point p2 = nodePositions.get(edge.target);
            
            if (p1 != null && p2 != null) {
                // Para determinar si un par de aristas explícitas constituye una línea "simple" (no dirigida),
                // necesitamos verificar si su inversa *explícita* también existe y ambas son de peso 1.
                boolean hasExplicitReverseWeight1 = false;
                for (Edge reverseEdge : graph.getExplicitEdges()) {
                    if (reverseEdge.source.equals(edge.target) && 
                        reverseEdge.target.equals(edge.source) && 
                        reverseEdge.weight == 1 &&
                        edge.weight == 1) { // Asegurarse de que ambas direcciones explícitas sean peso 1
                        hasExplicitReverseWeight1 = true;
                        break;
                    }
                }
                
                // Condición para dibujar como una línea simple no dirigida:
                // 1. Tanto esta arista como su inversa explícita existen y tienen peso 1.
                // 2. No involucra nodos especiales (los nodos especiales siempre se dibujan como dirigidos).
                // 3. El par no dirigido no ha sido dibujado aún (para evitar dibujar la misma línea dos veces).
                boolean isVisuallyUndirected = hasExplicitReverseWeight1 &&
                                                !graph.isSpecialNode(edge.source) && 
                                                !graph.isSpecialNode(edge.target);

                // Generar una clave única para el par no dirigido (independiente del orden)
                String undirectedPairKey = (edge.source.compareTo(edge.target) < 0) ? 
                                           (edge.source + "-" + edge.target) : 
                                           (edge.target + "-" + edge.source);

                if (isVisuallyUndirected && !drawnUndirectedPairs.contains(undirectedPairKey)) {
                    // Es una arista no dirigida simple: dibujar como una línea sin flecha
                    drawSimpleLine(g2d, p1.x, p1.y, p2.x, p2.y);
                    drawnUndirectedPairs.add(undirectedPairKey); // Marcar el par como dibujado
                } else if (!isVisuallyUndirected) { 
                    // Si NO es una arista visualmente no dirigida, o si es la inversa de un par ya dibujado (que saltamos)
                    // Es una arista explícitamente dirigida (peso > 1, o peso 1 pero sin inversa explícita, o involucra un nodo especial)
                    // Dibujar con una flecha y peso si aplica
                    drawArrowedLine(g2d, p1.x, p1.y, p2.x, p2.y, edge.weight);
                }
            }
        }

        g2d.setFont(new Font("Dialog", Font.BOLD, 18)); 
        for (String nodeName : graph.getAllNodes()) {
            Point p = nodePositions.get(nodeName);
            if (p == null) continue; 

            Color baseColor;
            if (graph.isSpecialNode(nodeName)) {
                baseColor = Color.RED.darker(); // Nodos especiales: Rojo Oscuro
            } else {
                String state = game.getNodeState(nodeName);
                switch (state) {
                    case "Happy":
                        baseColor = new Color(102, 204, 102); // Feliz: Verde Medio
                        break;
                    case "Sad":
                        baseColor = Color.BLUE.darker(); // Triste: Azul Oscuro
                        break;
                    case "Excited":
                        baseColor = Color.GREEN.darker(); // Emocionado: Verde Oscuro
                        break;
                    default:
                        baseColor = Color.GRAY;
                        break;
                }
            }

            // Sombra para el nodo
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.fillOval(p.x - NODE_SIZE / 2 + 5, p.y - NODE_SIZE / 2 + 5, NODE_SIZE, NODE_SIZE);

            // Gradiente para el nodo
            Color brighterColor = baseColor.brighter().brighter();
            Color darkerColor = baseColor.darker().darker();
            
            GradientPaint gradient = new GradientPaint(
                p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, brighterColor,
                p.x + NODE_SIZE / 2, p.y + NODE_SIZE / 2, darkerColor);
            g2d.setPaint(gradient);
            g2d.fillOval(p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, NODE_SIZE, NODE_SIZE);

            // Borde del nodo según el modo
            if ((isSimpleEdgeMode || isDirectedEdgeMode) && nodeName.equals(firstNodeSelectedForEdge)) { // Actualizado para ambos modos de arista
                g2d.setColor(Color.CYAN); // Resaltar nodo origen en modo arista
                g2d.setStroke(new BasicStroke(4));
                g2d.drawOval(p.x - NODE_SIZE / 2 - 2, p.y - NODE_SIZE / 2 - 2, NODE_SIZE + 4, NODE_SIZE + 4);
            } else if (isDeleteMode) {
                g2d.setColor(Color.RED); // Resaltar nodos en modo eliminar
                g2d.setStroke(new BasicStroke(4));
                g2d.drawOval(p.x - NODE_SIZE / 2 - 2, p.y - NODE_SIZE / 2 - 2, NODE_SIZE + 4, NODE_SIZE + 4);
            } else {
                g2d.setColor(Color.WHITE); // Borde normal
                g2d.setStroke(new BasicStroke(2));
                g2d.drawOval(p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, NODE_SIZE, NODE_SIZE);
            }


            g2d.setColor(Color.WHITE); // Color del texto
            
            String displayedNodeName = getNodeDisplayString(nodeName); 
            String chipsText = String.valueOf(graph.getChips(nodeName));

            FontMetrics fm = g2d.getFontMetrics();
            int nodeTextWidth = fm.stringWidth(displayedNodeName);
            int chipsTextWidth = fm.stringWidth(chipsText);

            int nodeTextX = p.x - nodeTextWidth / 2;
            int nodeTextY = p.y - fm.getHeight() / 2 + fm.getAscent() / 2 - (fm.getAscent() / 2 + 5); 

            int chipsTextX = p.x - chipsTextWidth / 2;
            int chipsTextY = p.y + fm.getHeight() / 2 + fm.getAscent() / 2 - (fm.getAscent() / 2 - 10); 

            g2d.drawString(displayedNodeName, nodeTextX, nodeTextY);
            g2d.drawString(chipsText, chipsTextX, chipsTextY);
        }
    }
    
    // Método para dibujar una línea simple (no dirigida)
    private void drawSimpleLine(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        g2d.drawLine(x1, y1, x2, y2);
    }

    // Método para dibujar una línea con flecha (dirigida) y opcionalmente el peso
    private void drawArrowedLine(Graphics2D g2d, int x1, int y1, int x2, int y2, int weight) {
        int ARR_SIZE = 10; // Tamaño de la punta de la flecha
        
        // Calcular el ángulo de la línea
        double angle = Math.atan2(y2 - y1, x2 - x1);
        
        // Ajustar el punto final de la línea para que la flecha no se superponga al nodo destino
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double ratio = (distance - NODE_SIZE / 2.0) / distance; // Restar el radio del nodo destino
        
        int adjustedX2 = (int) (x1 + dx * ratio);
        int adjustedY2 = (int) (y1 + dy * ratio);

        g2d.drawLine(x1, y1, adjustedX2, adjustedY2); // Dibujar la línea

        // Dibujar la punta de la flecha
        AffineTransform tx = g2d.getTransform();
        g2d.translate(adjustedX2, adjustedY2);
        g2d.rotate(angle);
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 0);
        arrowHead.addPoint(-ARR_SIZE, ARR_SIZE / 2);
        arrowHead.addPoint(-ARR_SIZE, -ARR_SIZE / 2);
        g2d.fill(arrowHead);
        g2d.setTransform(tx); // Restaurar la transformación

        // Dibujar el peso de la arista si es mayor que 1
        if (weight > 1) {
            String weightText = String.valueOf(weight);
            // Calcular el punto medio de la arista (un poco desplazado para no superponerse)
            int midX = (x1 + adjustedX2) / 2;
            int midY = (y1 + adjustedY2) / 2;
            
            // Desplazar el texto del peso perpendicular a la línea
            // Para evitar que el texto quede sobre la línea
            double perpendicularAngle = angle + Math.PI / 2;
            int offsetX = (int) (10 * Math.cos(perpendicularAngle));
            int offsetY = (int) (10 * Math.sin(perpendicularAngle));

            g2d.setColor(Color.WHITE); // Color del texto del peso
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(weightText);
            int textHeight = fm.getHeight();
            
            g2d.drawString(weightText, midX + offsetX - textWidth / 2, midY + offsetY + textHeight / 4);
        }
    }

    public void updateGraphDisplay() {
        repaint();
    }
    
    public void resetNodePositions() {
        nodePositions.clear();
        repaint();
    }
}

public class KostantGameGUI extends JFrame implements NodePlacementListener, EdgeCreationListener, NodeInteractionListener {

    private final Graph graph;
    private final KostantGame game;
    private final GraphPanel graphPanel;

    private final JTextField nodeInput;
    private final JTextField edgeSimpleInput; // Para mensajes de arista simple
    private final JTextField initialNodeInput;
    private final JTextArea messageArea;
    
    private final JButton toggleNodeModeButton;
    private final JButton toggleSimpleEdgeModeButton; // Botón para arista simple
    private final JButton toggleDirectedEdgeModeButton; // Nuevo: Botón para arista dirigida
    private final JSpinner directedEdgeWeightSpinner; // Nuevo: Spinner para el peso de arista dirigida
    private final JButton toggleDeleteModeButton;
    private final JButton initializeGameButton;
    private final JButton reflectButton;
    private final JButton playFullButton; 
    private final JButton stopAutoPlayButton; 
    private final JButton newGraphButton;
    private final JButton resetGameConfigButton;

    private JComboBox<String> targetNodeSelector;
    private JButton addSpecialNodeButton;

    private int nodeCounter = 1;
    private boolean isGameActive = false;
    private SwingWorker<Integer, String> autoPlayWorker; 
    private volatile int currentDelayMillis = 500; 
    private final int MIN_DELAY_MILLIS = 50; 

    public KostantGameGUI() {
        setTitle("Juego de Kostant modificado");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        graph = new Graph();
        game = new KostantGame(graph);
        graphPanel = new GraphPanel(graph, game, this, this, this); 

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setPreferredSize(new Dimension(350, getHeight()));
        controlPanel.setBackground(new Color(40, 40, 40));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messageArea = new JTextArea(5, 20);
        messageArea.setEditable(false);
        messageArea.setBackground(new Color(50, 50, 50));
        messageArea.setForeground(Color.LIGHT_GRAY);
        messageArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JScrollPane scrollPane = new JScrollPane(messageArea);
        controlPanel.add(scrollPane);
        controlPanel.add(Box.createVerticalStrut(10));

        JLabel nodeLabel = new JLabel("Añadir Nodo (Auto: αₓ)"); 
        nodeLabel.setForeground(Color.WHITE);
        controlPanel.add(nodeLabel);
        nodeInput = new JTextField(15);
        nodeInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, nodeInput.getPreferredSize().height));
        nodeInput.setEditable(false);
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        toggleNodeModeButton = new JButton("Activar Modo Nodo");
        toggleNodeModeButton.setBackground(new Color(70, 130, 180));
        toggleNodeModeButton.setForeground(Color.WHITE);
        toggleNodeModeButton.setFocusPainted(false);
        toggleNodeModeButton.addActionListener(e -> toggleNodeMode());
        controlPanel.add(nodeInput);
        controlPanel.add(toggleNodeModeButton);
        controlPanel.add(Box.createVerticalStrut(10));

        // Sección para Aristas Simples (No Dirigidas)
        JLabel simpleEdgeLabel = new JLabel("Añadir Arista Simple (No Dirigida)");
        simpleEdgeLabel.setForeground(Color.WHITE);
        controlPanel.add(simpleEdgeLabel);
        edgeSimpleInput = new JTextField("Clic en 2 nodos..."); 
        edgeSimpleInput.setEditable(false); 
        edgeSimpleInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, edgeSimpleInput.getPreferredSize().height));
        
        toggleSimpleEdgeModeButton = new JButton("Activar Modo Arista Simple"); 
        toggleSimpleEdgeModeButton.setBackground(new Color(70, 130, 180));
        toggleSimpleEdgeModeButton.setForeground(Color.WHITE);
        toggleSimpleEdgeModeButton.setFocusPainted(false);
        toggleSimpleEdgeModeButton.addActionListener(e -> toggleSimpleEdgeMode());
        controlPanel.add(edgeSimpleInput);
        controlPanel.add(toggleSimpleEdgeModeButton); 
        controlPanel.add(Box.createVerticalStrut(10));

        // Sección para Aristas Dirigidas (con peso) - Ahora con selección manual
        JLabel directedEdgeLabel = new JLabel("Añadir Arista Dirigida");
        directedEdgeLabel.setForeground(Color.WHITE);
        controlPanel.add(directedEdgeLabel);

        JPanel directedEdgeInputPanel = new JPanel();
        directedEdgeInputPanel.setLayout(new BoxLayout(directedEdgeInputPanel, BoxLayout.X_AXIS));
        directedEdgeInputPanel.setBackground(new Color(40,40,40));
        
        JLabel weightLabel = new JLabel("Peso:");
        weightLabel.setForeground(Color.WHITE);
        directedEdgeInputPanel.add(weightLabel);
        directedEdgeInputPanel.add(Box.createHorizontalStrut(5));

        SpinnerModel weightModel = new SpinnerNumberModel(1, 1, 10, 1); // Valor inicial 1, mínimo 1, máximo 10, paso 1
        directedEdgeWeightSpinner = new JSpinner(weightModel);
        directedEdgeWeightSpinner.setMaximumSize(new Dimension(80, directedEdgeWeightSpinner.getPreferredSize().height));
        ((JSpinner.DefaultEditor) directedEdgeWeightSpinner.getEditor()).getTextField().setEditable(false); // No permitir edición de texto
        directedEdgeInputPanel.add(directedEdgeWeightSpinner);
        directedEdgeInputPanel.add(Box.createHorizontalGlue()); // Para empujar el spinner a la izquierda
        
        controlPanel.add(directedEdgeInputPanel);
        controlPanel.add(Box.createVerticalStrut(5)); // Espacio entre spinner y botón
        
        toggleDirectedEdgeModeButton = new JButton("Activar Modo Arista Dirigida");
        toggleDirectedEdgeModeButton.setBackground(new Color(70, 130, 180).darker());
        toggleDirectedEdgeModeButton.setForeground(Color.WHITE);
        toggleDirectedEdgeModeButton.setFocusPainted(false);
        toggleDirectedEdgeModeButton.addActionListener(e -> toggleDirectedEdgeMode());
        controlPanel.add(toggleDirectedEdgeModeButton);
        controlPanel.add(Box.createVerticalStrut(10));

        toggleDeleteModeButton = new JButton("Eliminar Nodo");
        toggleDeleteModeButton.setBackground(new Color(180, 50, 50));
        toggleDeleteModeButton.setForeground(Color.WHITE);
        toggleDeleteModeButton.setFocusPainted(false);
        toggleDeleteModeButton.addActionListener(e -> toggleDeleteMode());
        controlPanel.add(toggleDeleteModeButton);
        controlPanel.add(Box.createVerticalStrut(10));

        JPanel specialNodePanel = new JPanel();
        specialNodePanel.setLayout(new BoxLayout(specialNodePanel, BoxLayout.Y_AXIS));
        specialNodePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Añadir Nodo Siempre Feliz", SwingConstants.CENTER, SwingConstants.TOP, new Font("Arial", Font.BOLD, 12), Color.WHITE));
        specialNodePanel.setBackground(new Color(40, 40, 40));
        specialNodePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel specialNodeLabel = new JLabel("Conectar nodo especial (αₓ') a:"); 
        specialNodeLabel.setForeground(Color.WHITE);
        specialNodeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        specialNodePanel.add(specialNodeLabel);

        targetNodeSelector = new JComboBox<>();
        targetNodeSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetNodeSelector.getPreferredSize().height));
        targetNodeSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
        specialNodePanel.add(targetNodeSelector); // Corrected from targetNodeNodeSelector

        addSpecialNodeButton = new JButton("Añadir Nodo Siempre Feliz");
        addSpecialNodeButton.setBackground(new Color(180, 100, 20));
        addSpecialNodeButton.setForeground(Color.WHITE);
        addSpecialNodeButton.setFocusPainted(false);
        addSpecialNodeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        addSpecialNodeButton.addActionListener(e -> addSpecialNode());
        specialNodePanel.add(addSpecialNodeButton);
        specialNodePanel.add(Box.createVerticalStrut(10));
        controlPanel.add(specialNodePanel);
        controlPanel.add(Box.createVerticalStrut(10));

        JLabel initialNodeLabel = new JLabel("Nodo inicial para juego estándar (ej: α₁):"); 
        initialNodeLabel.setForeground(Color.WHITE);
        controlPanel.add(initialNodeLabel);
        initialNodeInput = new JTextField(15);
        initialNodeInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, initialNodeInput.getPreferredSize().height));
        initializeGameButton = new JButton("Inicializar Juego");
        initializeGameButton.setBackground(new Color(50, 110, 160));
        initializeGameButton.setForeground(Color.WHITE);
        initializeGameButton.setFocusPainted(false);
        initializeGameButton.addActionListener(e -> initializeGame());
        controlPanel.add(initialNodeInput);
        controlPanel.add(initializeGameButton);
        controlPanel.add(Box.createVerticalStrut(10));

        initialNodeInput.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                setControlsEnabled(true);
            }
            public void removeUpdate(DocumentEvent e) {
                setControlsEnabled(true);
            }
            public void insertUpdate(DocumentEvent e) {
                setControlsEnabled(true);
            }
        });

        reflectButton = new JButton("Realizar 1 Reflexión");
        reflectButton.setBackground(new Color(50, 110, 160));
        reflectButton.setForeground(Color.WHITE);
        reflectButton.setFocusPainted(false);
        reflectButton.addActionListener(e -> performReflection());
        controlPanel.add(reflectButton);
        controlPanel.add(Box.createVerticalStrut(5));

        playFullButton = new JButton("Jugar Automático"); 
        playFullButton.setBackground(new Color(50, 110, 160));
        playFullButton.setForeground(Color.WHITE);
        playFullButton.setFocusPainted(false);
        playFullButton.addActionListener(e -> playFullGame());
        controlPanel.add(playFullButton);
        controlPanel.add(Box.createVerticalStrut(5)); 

        stopAutoPlayButton = new JButton("Detener Automático");
        stopAutoPlayButton.setBackground(new Color(220, 50, 50)); 
        stopAutoPlayButton.setForeground(Color.WHITE);
        stopAutoPlayButton.setFocusPainted(false);
        stopAutoPlayButton.addActionListener(e -> stopAutoPlay());
        controlPanel.add(stopAutoPlayButton);
        controlPanel.add(Box.createVerticalStrut(10));


        resetGameConfigButton = new JButton("Reiniciar Juego");
        resetGameConfigButton.setBackground(new Color(200, 100, 0));
        resetGameConfigButton.setForeground(Color.WHITE);
        resetGameConfigButton.setFocusPainted(false);
        resetGameConfigButton.addActionListener(e -> resetGameConfiguration());
        controlPanel.add(resetGameConfigButton);
        controlPanel.add(Box.createVerticalStrut(5));

        newGraphButton = new JButton("Nuevo Grafo / Reiniciar Todo");
        newGraphButton.setBackground(new Color(34, 139, 34));
        newGraphButton.setForeground(Color.WHITE);
        newGraphButton.setFocusPainted(false);
        newGraphButton.addActionListener(e -> resetApplication());
        controlPanel.add(newGraphButton);
        
        controlPanel.add(Box.createVerticalGlue());

        JPanel legendPanel = new JPanel();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBackground(new Color(40, 40, 40));
        legendPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Leyenda de Estados", SwingConstants.CENTER, SwingConstants.TOP, new Font("Arial", Font.BOLD, 12), Color.WHITE));

        addLegendEntry(legendPanel, "Feliz (Nodo estándar)", new Color(102, 204, 102));
        addLegendEntry(legendPanel, "Triste (Nodo estándar)", Color.BLUE.darker()); 
        addLegendEntry(legendPanel, "Emocionado (Nodo estándar)", Color.GREEN.darker());
        addLegendEntry(legendPanel, "Siempre Feliz (Nodo Especial)", Color.RED.darker());

        legendPanel.add(Box.createVerticalStrut(10));
        JLabel attributionLabel = new JLabel("<html><div style='text-align: center;'>Aplicación programada por Juan Sebastián Cortés Cruz<br>para la tesis Grupos de Weyl y el juego de Kostant</div></html>");
        attributionLabel.setForeground(Color.LIGHT_GRAY);
        attributionLabel.setFont(new Font("Arial", Font.ITALIC, 10));
        attributionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        legendPanel.add(attributionLabel);

        controlPanel.add(legendPanel);

        add(controlPanel, BorderLayout.WEST);
        add(graphPanel, BorderLayout.CENTER);

        resetApplication();
    }

    private void addLegendEntry(JPanel panel, String text, Color color) {
        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        entry.setBackground(new Color(40, 40, 40));

        JLabel colorSquare = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(color);
                g.fillRect(0, 0, 15, 15);
                g.setColor(Color.WHITE);
                g.drawRect(0, 0, 15, 15);
            }
        };
        colorSquare.setPreferredSize(new Dimension(15, 15));

        JLabel description = new JLabel(text);
        description.setForeground(Color.WHITE);

        entry.add(colorSquare);
        entry.add(description);
        panel.add(entry);
    }
    
    private void toggleNodeMode() {
        if (graphPanel.isNodeMode()) {
            graphPanel.setNodeMode(false);
            showMessage("Modo Nodo cancelado.", "Info");
            setControlsEnabled(true);
            toggleNodeModeButton.setText("Activar Modo Nodo");
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        } else {
            if (graphPanel.isSimpleEdgeMode()) { // Desactivar modo arista simple
                toggleSimpleEdgeMode();
            }
            if (graphPanel.isDirectedEdgeMode()) { // Desactivar modo arista dirigida
                toggleDirectedEdgeMode();
            }
            if (graphPanel.isDeleteMode()) {
                toggleDeleteMode();
            }

            graphPanel.setNodeMode(true);
            String nodeNameLogical = String.valueOf(nodeCounter);
            graphPanel.setPendingNodeName(nodeNameLogical);
            showMessage("Modo Nodo activado. Haz clic en el tablero para ubicar el nodo '" + graphPanel.getNodeDisplayString(nodeNameLogical) + "'.", "Info");
            
            setControlsEnabled(false);
            toggleNodeModeButton.setEnabled(true);
            toggleNodeModeButton.setText("Cancelar Modo Nodo");
            newGraphButton.setEnabled(true);
        }
    }
    
    @Override
    public void onNodePlaced(String nodeName) {
        nodeCounter++;
        showMessage("Nodo '" + graphPanel.getNodeDisplayString(nodeName) + "' colocado correctamente. Haz clic para ubicar el siguiente nodo.", "Info");
        graphPanel.setPendingNodeName(String.valueOf(nodeCounter)); 
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        graphPanel.updateGraphDisplay();

        if (graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2) {
            toggleSimpleEdgeModeButton.setEnabled(true); // Habilitar arista simple
            toggleDirectedEdgeModeButton.setEnabled(true); // Habilitar arista dirigida
        }
        if (graph.getAllNodes().stream().anyMatch(node -> !graph.isSpecialNode(node))) {
            addSpecialNodeButton.setEnabled(true);
            targetNodeSelector.setEnabled(true);
            populateTargetNodeSelector();
        }
        setControlsEnabled(true);
    }

    @Override
    public void onPlacementError(String message) {
        showMessage("Error de ubicación: " + message, "Error");
    }
    
    @Override
    public void onNodePlacementCancelled(String message) {
        showMessage(message, "Warning");
        setControlsEnabled(true);
        toggleNodeModeButton.setText("Activar Modo Nodo");
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
    }


    // Nuevo método para el botón de Arista Simple (No Dirigida)
    private void toggleSimpleEdgeMode() {
        if (graphPanel.isSimpleEdgeMode()) {
            graphPanel.resetSimpleEdgeMode();
            showMessage("Modo Arista Simple cancelado.", "Info");
            setControlsEnabled(true);
            toggleSimpleEdgeModeButton.setText("Activar Modo Arista Simple");
            edgeSimpleInput.setText("Clic en 2 nodos...");
        } else {
            if (graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() < 2) {
                showMessage("Necesitas al menos 2 nodos regulares para crear una arista simple.", "Error");
                return;
            }
            if (graphPanel.isNodeMode()) {
                toggleNodeMode();
            }
            if (graphPanel.isDirectedEdgeMode()) { // Desactivar modo arista dirigida
                toggleDirectedEdgeMode();
            }
            if (graphPanel.isDeleteMode()) {
                toggleDeleteMode();
            }

            graphPanel.startSimpleEdgeMode();
            setControlsEnabled(false);
            toggleSimpleEdgeModeButton.setEnabled(true);
            toggleSimpleEdgeModeButton.setText("Cancelar Modo Arista Simple");
            showMessage("Modo Arista Simple activado. Haz clic en el PRIMER nodo para la conexión.", "Info");
            edgeSimpleInput.setText("Selecciona el primer nodo...");
            newGraphButton.setEnabled(true);
        }
    }

    // Nuevo método para el botón de Arista Dirigida
    private void toggleDirectedEdgeMode() {
        if (graphPanel.isDirectedEdgeMode()) {
            graphPanel.resetDirectedEdgeMode();
            showMessage("Modo Arista Dirigida cancelado.", "Info");
            setControlsEnabled(true);
            toggleDirectedEdgeModeButton.setText("Activar Modo Arista Dirigida");
        } else {
            if (graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() < 2) {
                showMessage("Necesitas al menos 2 nodos regulares para crear una arista dirigida.", "Error");
                return;
            }
            if (graphPanel.isNodeMode()) {
                toggleNodeMode();
            }
            if (graphPanel.isSimpleEdgeMode()) { // Desactivar modo arista simple
                toggleSimpleEdgeMode();
            }
            if (graphPanel.isDeleteMode()) {
                toggleDeleteMode();
            }

            int selectedWeight = (Integer) directedEdgeWeightSpinner.getValue();
            graphPanel.startDirectedEdgeMode(selectedWeight);
            setControlsEnabled(false);
            toggleDirectedEdgeModeButton.setEnabled(true);
            toggleDirectedEdgeModeButton.setText("Cancelar Modo Arista Dirigida");
            showMessage("Modo Arista Dirigida (Peso: " + selectedWeight + ") activado. Haz clic en el NODO ORIGEN.", "Info");
            newGraphButton.setEnabled(true);
        }
    }


    // Reutilizar onEdgeCreated, añadiendo los parámetros isUndirected y isDirected
    @Override
    public void onEdgeCreated(String sourceNode, String targetNode, boolean isUndirected, boolean isDirected) {
        if (targetNode == null) {
            String modeMessage = isUndirected ? "Primer nodo seleccionado para arista simple: '" : "Nodo origen seleccionado para arista dirigida: '";
            String nextActionMessage = isUndirected ? "Haz clic en el SEGUNDO nodo." : "Haz clic en el NODO DESTINO.";
            showMessage(modeMessage + graphPanel.getNodeDisplayString(sourceNode) + "'. " + nextActionMessage, "Info");
            if (isUndirected) edgeSimpleInput.setText("Selecciona el segundo nodo...");
        } else {
            if (isUndirected) {
                showMessage("Arista no dirigida creada entre '" + graphPanel.getNodeDisplayString(sourceNode) + "' y '" + graphPanel.getNodeDisplayString(targetNode) + "'.", "Success");
                edgeSimpleInput.setText("Arista creada. Selecciona el PRIMER nodo para la siguiente...");
            } else if (isDirected) {
                // Para aristas dirigidas, necesitamos el peso real que se acaba de añadir explícitamente
                // Este peso es el que el usuario seleccionó con el spinner.
                // El graph.getEdgeWeight(sourceNode, targetNode) ahora devuelve el peso EFECTIVO (incluyendo implícitos)
                // Pero aquí queremos el peso de la arista explícita que se acaba de crear.
                // La variable 'currentDirectedEdgeWeight' del GraphPanel tiene el peso que se usó.
                showMessage("Arista dirigida '" + graphPanel.getNodeDisplayString(sourceNode) + "' -> '" + graphPanel.getNodeDisplayString(targetNode) + "' (Peso: " + graphPanel.getCurrentDirectedEdgeWeight() + ") creada.", "Success");
            }
        }
        if (!graph.getExplicitEdges().isEmpty()) { // Usar getExplicitEdges para habilitar inicialización
            initializeGameButton.setEnabled(true);
        }
        setControlsEnabled(true);
    }


    @Override
    public void onEdgeCreationCancelled(String message) {
        showMessage(message, "Warning");
        setControlsEnabled(true);
        edgeSimpleInput.setText("Clic en 2 nodos..."); // Ajustado para arista simple
        toggleSimpleEdgeModeButton.setText("Activar Modo Arista Simple");
        toggleDirectedEdgeModeButton.setText("Activar Modo Arista Dirigida");
    }

    @Override
    public void onEdgeCreationError(String message) {
        showMessage("Error al crear arista: " + message, "Error");
        edgeSimpleInput.setText("Clic en 2 nodos..."); // Ajustado para arista simple
    }

    private void toggleDeleteMode() {
        if (graphPanel.isDeleteMode()) {
            graphPanel.setDeleteMode(false);
            showMessage("Modo Eliminar Nodo cancelado.", "Info");
            setControlsEnabled(true);
            toggleDeleteModeButton.setText("Eliminar Nodo");
        } else {
            if (graphPanel.isNodeMode()) {
                toggleNodeMode();
            }
            if (graphPanel.isSimpleEdgeMode()) { // Desactivar modo arista simple
                toggleSimpleEdgeMode();
            }
            if (graphPanel.isDirectedEdgeMode()) { // Desactivar modo arista dirigida
                toggleDirectedEdgeMode();
            }

            graphPanel.setDeleteMode(true);
            showMessage("Modo Eliminar Nodo activado. Haz clic en el nodo que deseas eliminar.", "Info");
            
            setControlsEnabled(false);
            toggleDeleteModeButton.setEnabled(true);
            toggleDeleteModeButton.setText("Cancelar Eliminación");
            newGraphButton.setEnabled(true);
        }
    }

    private void populateTargetNodeSelector() {
        targetNodeSelector.removeAllItems();
        List<String> allNodes = graph.getAllNodes();
        for (String nodeLogicalName : allNodes) {
            // Solo añadir nodos regulares (no especiales) que no tengan ya un nodo especial asociado
            if (!graph.isSpecialNode(nodeLogicalName) && !graph.isSpecialNode(nodeLogicalName + "'")) {
                targetNodeSelector.addItem(graphPanel.getNodeDisplayString(nodeLogicalName));
            }
        }
    }

    private void addSpecialNode() {
        String selectedDisplayNode = (String) targetNodeSelector.getSelectedItem();
        if (selectedDisplayNode == null || selectedDisplayNode.isEmpty()) {
            showMessage("Por favor, selecciona un nodo del grafo al que conectar el nodo especial.", "Error");
            return;
        }
        
        String targetLogicalName = convertSubscriptToNormal(selectedDisplayNode); 

        try {
            String newSpecialNodeLogicalName = graph.addSpecialNode(targetLogicalName); 
            Point targetPos = graphPanel.getNodePosition(targetLogicalName); 
            Point newSpecialNodePos;
            if (targetPos != null) {
                // Posicionar el nodo especial un poco encima del nodo base
                newSpecialNodePos = new Point(targetPos.x, targetPos.y - (graphPanel.getNodeSize() + 10));
            } else {
                // Fallback si la posición del nodo base no se encuentra (debería existir)
                newSpecialNodePos = new Point(getWidth() / 2 + (int)(Math.random() * 100 - 50), getHeight() / 2 + (int)(Math.random() * 100 - 50)); 
            }

            graphPanel.addNodePosition(newSpecialNodeLogicalName, newSpecialNodePos);
            
            showMessage("Nodo especial '" + graphPanel.getNodeDisplayString(newSpecialNodeLogicalName) + "' añadido y conectado a '" + selectedDisplayNode + "'.", "Success");
            setControlsEnabled(true);
        }
        catch (IllegalStateException | IllegalArgumentException e) {
            showMessage("Error al añadir nodo especial: " + e.getMessage(), "Error");
        }
    }

    // Convierte el nombre de visualización (ej. "α₁") a nombre lógico (ej. "1")
    private String convertSubscriptToNormal(String displayString) {
        StringBuilder normal = new StringBuilder();
        String numberPart = displayString;
        boolean hasApostrophe = false;

        if (numberPart.endsWith("'")) {
            hasApostrophe = true;
            numberPart = numberPart.substring(0, numberPart.length() - 1);
        }

        if (numberPart.startsWith("α")) {
            numberPart = numberPart.substring(1);
        }

        for (char c : numberPart.toCharArray()) {
            switch (c) {
                case '₀': normal.append('0'); break;
                case '₁': normal.append('1'); break; 
                case '₂': normal.append('2'); break;
                case '₃': normal.append('3'); break;
                case '₄': normal.append('4'); break;
                case '₅': normal.append('5'); break;
                case '₆': normal.append('6'); break;
                case '₇': normal.append('7'); break;
                case '₈': normal.append('8'); break;
                case '₉': normal.append('9'); break;
                default: normal.append(c); 
            }
        }
        if (hasApostrophe) {
            normal.append("'");
        }
        return normal.toString();
    }


    @Override
    public void onNodeClicked(String nodeLogicalName) {
        if (graphPanel.isDeleteMode()) {
            if (nodeLogicalName == null) {
                toggleDeleteMode();
                showMessage("Eliminación cancelada. Clic fuera de un nodo.", "Warning");
                return;
            }
            
            int dialogResult = JOptionPane.showConfirmDialog(this, 
                                "Estás seguro que quieres eliminar el nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' y sus aristas?", 
                                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION);
            if (dialogResult == JOptionPane.YES_OPTION) {
                graph.removeNode(nodeLogicalName); 
                graphPanel.removeNodePosition(nodeLogicalName); 
                showMessage("Nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' eliminado correctamente.", "Success");
                setControlsEnabled(true);
            } else {
                showMessage("Eliminación del nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' cancelada.", "Info");
            }
            toggleDeleteMode();
        } else if (!graphPanel.isNodeMode() && !graphPanel.isSimpleEdgeMode() && !graphPanel.isDirectedEdgeMode() && !graphPanel.isDeleteMode() && isGameActive) { // Asegurarse que no esté en ningún modo de edición
            if (graph.isSpecialNode(nodeLogicalName)) {
                showMessage("El nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' es un nodo siempre feliz y no puede ser reflejado.", "Warning");
            } else if (game.getNodeState(nodeLogicalName).equals("Sad")) { 
                try {
                    game.performReflection(nodeLogicalName);
                    graphPanel.updateGraphDisplay();
                    showMessage("Reflexión realizada en el nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' por clic.", "Info");
                    setControlsEnabled(true);
                }
                catch (IllegalStateException e) {
                    showMessage("Error de juego al reflejar: " + e.getMessage(), "Error");
                }
            } else {
                showMessage("El nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' no está triste y no puede ser reflejado.", "Warning"); 
            }
        } else if (!isGameActive) {
             showMessage("Primero debes inicializar el juego para realizar reflexiones.", "Warning");
        }
    }

    private void initializeGame() {
        String initialNodeInputText = initialNodeInput.getText().trim();
        String initialNodeLogicalName = convertSubscriptToNormal(initialNodeInputText); 

        try {
            game.initializeGame(initialNodeLogicalName); 
            graphPanel.updateGraphDisplay();
            
            isGameActive = true;

            if (!graph.getSpecialNodesLogicalNames().isEmpty()) {
                showMessage("Juego modificado inicializado. Todos los nodos especiales tienen 1 chip.", "Info");
            } else {
                 showMessage("Juego estándar inicializado con nodo '" + graphPanel.getNodeDisplayString(initialNodeLogicalName) + "'.", "Info");
            }
            
            setControlsEnabled(true);
        }
        catch (IllegalArgumentException e) {
            showMessage("Error inicializando juego: " + e.getMessage(), "Error");
            isGameActive = false;
            setControlsEnabled(true);
        }
    }

    private void performReflection() {
        if (!isGameActive) {
            showMessage("El juego no está inicializado. Inicialízalo primero para realizar reflexiones.", "Warning");
            return;
        }

        List<String> unhappyNodes = game.getUnhappyNodes();
        if (unhappyNodes.isEmpty()) {
            showMessage("No hay nodos tristes para reflejar. El juego ha convergido.", "Info"); 
            return;
        }
        
        String nodeToReflect = unhappyNodes.get(0); 
        try {
            game.performReflection(nodeToReflect);
            graphPanel.updateGraphDisplay();
            showMessage("Reflexión realizada en el nodo '" + graphPanel.getNodeDisplayString(nodeToReflect) + "'.", "Info");
            setControlsEnabled(true); 
        }
        catch (IllegalStateException e) {
            showMessage("Error del juego: " + e.getMessage(), "Error");
        }
    }

    private void playFullGame() {
        if (!isGameActive) {
            showMessage("El juego no está inicializado. Inicialízalo primero para jugar automático.", "Warning");
            return;
        }
        if (game.getUnhappyNodes().isEmpty()) {
            showMessage("No hay nodos tristes. El juego ya está convergido.", "Info"); 
            return;
        }
        
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            // Si ya está ejecutándose, acelerarlo
            currentDelayMillis = Math.max(MIN_DELAY_MILLIS, currentDelayMillis / 2);
            showMessage("Juego automático acelerado. Retraso: " + currentDelayMillis + "ms", "Info");
            return; // No iniciar un nuevo worker, solo ajustar la velocidad
        }

        // Deshabilitar todos los controles mientras el juego automático se ejecuta
        setControlsEnabled(false); 
        newGraphButton.setEnabled(true); 
        resetGameConfigButton.setEnabled(true);
        stopAutoPlayButton.setEnabled(true); 

        showMessage("Iniciando juego automático... Por favor, espera.", "Info"); 
        
        // Reiniciar el retraso al iniciar una nueva sesión de juego automático
        currentDelayMillis = 500; 

        autoPlayWorker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int stepsTaken = 0;
                int maxSteps = 500; // Límite para prevenir bucles infinitos

                while (stepsTaken < maxSteps) {
                    // Verificar cancelación al principio de cada iteración
                    if (isCancelled()) {
                        break; 
                    }

                    List<String> unhappyNodes = game.getUnhappyNodes();
                    if (unhappyNodes.isEmpty()) {
                        publish("El juego ha convergido: No quedan nodos tristes.");
                        break;
                    }

                    String nodeToReflect = unhappyNodes.get(0);
                    try {
                        game.performReflection(nodeToReflect);
                        publish(""); // Publicar una cadena vacía para activar la repintada de la UI en el EDT
                        
                        // Verificar cancelación antes de dormir
                        if (isCancelled()) {
                            break;
                        }
                        Thread.sleep(currentDelayMillis); // Usar el retraso dinámico
                    }
                    catch (IllegalStateException e) {
                        publish("Error inesperado durante la reflexión automática: " + e.getMessage());
                        break;
                    }
                    catch (InterruptedException e) {
                        // Manejar interrupción específicamente, a menudo significa cancelación
                        publish("Juego automático interrumpido.");
                        Thread.currentThread().interrupt(); // Restaurar el estado interrumpido
                        break;
                    }
                    stepsTaken++;
                }
                if (stepsTaken >= maxSteps) {
                    publish("El juego se detuvo después de " + maxSteps + " pasos (límite alcanzado). Todavía pueden quedar nodos tristes.");
                }
                return stepsTaken;
            }

            @Override
            protected void process(List<String> chunks) {
                // Este método se ejecuta en el Event Dispatch Thread (EDT)
                for (String messageChunk : chunks) {
                    if (!messageChunk.isEmpty()) {
                        showMessage(messageChunk, "Info");
                    }
                }
                graphPanel.updateGraphDisplay(); // Actualizar la visualización después de cada paso
            }

            @Override
            protected void done() {
                autoPlayWorker = null; // Limpiar la referencia del worker una vez que ha terminado o se ha cancelado
                try {
                    // Verificar si el worker fue cancelado
                    if (isCancelled()) {
                        showMessage("Juego automático cancelado.", "Warning"); 
                        return;
                    }

                    Integer stepsTaken = get();
                    List<String> remainingUnhappy = game.getUnhappyNodes();
                    if (remainingUnhappy.isEmpty()) {
                        showMessage("Juego automático completado en " + stepsTaken + " pasos. Todos los nodos están felices/emocionados.", "Success"); 
                    } else {
                        showMessage("Juego automático detenido después de " + stepsTaken + " pasos. Aún quedan nodos tristes: " + remainingUnhappy + ".", "Warning"); 
                    }
                }
                catch (Exception e) {
                    showMessage("Error al ejecutar el juego automático: " + e.getMessage(), "Error"); 
                    e.printStackTrace();
                } finally {
                    graphPanel.updateGraphDisplay();
                    setControlsEnabled(true); // Re-habilitar todos los controles
                }
            }
        };
        autoPlayWorker.execute();
    }

    private void stopAutoPlay() {
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
            showMessage("Se ha solicitado detener el juego automático.", "Info");
        } else {
            showMessage("El juego automático no está activo para detenerlo.", "Warning");
        }
    }

    private void resetGameConfiguration() {
        // Cancelar el autoPlayWorker si se está ejecutando
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
        }

        for (String node : graph.getAllNodes()) {
            graph.setChips(node, 0);
        }
        for (String specialNode : graph.getSpecialNodesLogicalNames()) {
            graph.setChips(specialNode, 1);
        }

        isGameActive = false;
        graphPanel.updateGraphDisplay();
        showMessage("Configuración del juego reiniciada. Los chips han vuelto a su estado inicial. Puedes inicializar el juego de nuevo.", "Info");
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        // Deshabilitar todos los controles de creación/eliminación/configuración del juego si un modo está activo
        if (graphPanel.isNodeMode() || graphPanel.isSimpleEdgeMode() || graphPanel.isDirectedEdgeMode() || graphPanel.isDeleteMode()) { 
            toggleNodeModeButton.setEnabled(graphPanel.isNodeMode());
            toggleNodeModeButton.setText(graphPanel.isNodeMode() ? "Cancelar Modo Nodo" : "Activar Modo Nodo");

            toggleSimpleEdgeModeButton.setEnabled(graphPanel.isSimpleEdgeMode()); 
            toggleSimpleEdgeModeButton.setText(graphPanel.isSimpleEdgeMode() ? "Cancelar Modo Arista Simple" : "Activar Modo Arista Simple"); 

            toggleDirectedEdgeModeButton.setEnabled(graphPanel.isDirectedEdgeMode()); // Nuevo: Habilitar/deshabilitar botón
            toggleDirectedEdgeModeButton.setText(graphPanel.isDirectedEdgeMode() ? "Cancelar Modo Arista Dirigida" : "Activar Modo Arista Dirigida"); // Nuevo: Texto del botón

            toggleDeleteModeButton.setEnabled(graphPanel.isDeleteMode());
            toggleDeleteModeButton.setText(graphPanel.isDeleteMode() ? "Cancelar Eliminación" : "Eliminar Nodo");

            // Deshabilitar otros botones mientras se está en un modo de edición
            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false); 
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
            initialNodeInput.setEnabled(false);
            resetGameConfigButton.setEnabled(false);
            directedEdgeWeightSpinner.setEnabled(false); // Nuevo: Deshabilitar spinner
        } 
        else if (isGameActive) { // Si el juego está activo
            // Deshabilitar botones de construcción/edición del grafo
            toggleNodeModeButton.setEnabled(false);
            toggleSimpleEdgeModeButton.setEnabled(false); 
            toggleDirectedEdgeModeButton.setEnabled(false); // Nuevo
            directedEdgeWeightSpinner.setEnabled(false); // Nuevo
            toggleDeleteModeButton.setEnabled(false);
            initializeGameButton.setEnabled(false);
            initialNodeInput.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);


            boolean hasUnhappyNodes = !game.getUnhappyNodes().isEmpty();
            boolean isAutoPlaying = (autoPlayWorker != null && !autoPlayWorker.isDone());

            reflectButton.setEnabled(hasUnhappyNodes && !isAutoPlaying); // Solo habilitar si no hay juego automático
            playFullButton.setEnabled(hasUnhappyNodes); // Siempre habilitado para permitir aceleración
            stopAutoPlayButton.setEnabled(isAutoPlaying); // Habilitar solo si hay juego automático
            resetGameConfigButton.setEnabled(true); // Siempre permitido reiniciar el estado del juego
        }
        else { // Estado predeterminado (ningún modo activo, juego no inicializado)
            toggleNodeModeButton.setEnabled(enabled);
            toggleNodeModeButton.setText("Activar Modo Nodo");
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));

            // Habilitar añadir arista simple por clic solo si hay al menos 2 nodos regulares
            toggleSimpleEdgeModeButton.setEnabled(enabled && graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2); 
            toggleSimpleEdgeModeButton.setText("Activar Modo Arista Simple"); 
            edgeSimpleInput.setText("Clic en 2 nodos...");
            
            // Habilitar añadir arista dirigida por clic solo si hay al menos 2 nodos regulares
            toggleDirectedEdgeModeButton.setEnabled(enabled && graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2); // Nuevo
            toggleDirectedEdgeModeButton.setText("Activar Modo Arista Dirigida"); // Nuevo
            directedEdgeWeightSpinner.setEnabled(enabled); // Nuevo: Habilitar spinner
            
            toggleDeleteModeButton.setEnabled(enabled && !graph.getAllNodes().isEmpty());
            toggleDeleteModeButton.setText("Eliminar Nodo");

            boolean hasRegularNodes = graph.getAllNodes().stream().anyMatch(node -> !graph.isSpecialNode(node));
            boolean canAddMoreSpecialNodes = false;
            if (hasRegularNodes) {
                for (String nodeLogicalName : graph.getAllNodes()) {
                    if (!graph.isSpecialNode(nodeLogicalName)) {
                        // Puede añadir un nodo especial si el nodo regular no tiene ya uno asociado
                        if (!graph.isSpecialNode(nodeLogicalName + "'")) { 
                            canAddMoreSpecialNodes = true;
                            break;
                        }
                    }
                }
            }

            addSpecialNodeButton.setEnabled(enabled && canAddMoreSpecialNodes);
            targetNodeSelector.setEnabled(enabled && canAddMoreSpecialNodes);
            if (addSpecialNodeButton.isEnabled()) {
                populateTargetNodeSelector();
            } else {
                targetNodeSelector.removeAllItems(); // Limpiar selector si no se puede añadir
            }
            
            boolean hasAnyNodes = !graph.getAllNodes().isEmpty();
            boolean hasExplicitEdges = !graph.getExplicitEdges().isEmpty(); // Cambiado a getExplicitEdges()
            boolean hasSpecialNodes = !graph.getSpecialNodesLogicalNames().isEmpty();
            // Inicializar juego habilitado si hay nodos especiales O si hay nodos y se ha introducido un nodo inicial
            initializeGameButton.setEnabled(enabled && (hasSpecialNodes || (hasAnyNodes && !initialNodeInput.getText().trim().isEmpty() && hasExplicitEdges))); // También se requieren aristas explícitas
            initialNodeInput.setEnabled(enabled && !hasSpecialNodes && hasAnyNodes); // Habilitar input si no hay especiales

            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            resetGameConfigButton.setEnabled(enabled && hasAnyNodes); // Habilitar si hay nodos para reiniciar
        }
        
        newGraphButton.setEnabled(true); // Este botón siempre está habilitado
    }

    private void resetApplication() {
        // Cancelar el autoPlayWorker si se está ejecutando
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
        }

        graphPanel.resetNodePositions();
        graphPanel.setNodeMode(false);
        graphPanel.resetSimpleEdgeMode(); // Resetea el modo de arista simple
        graphPanel.resetDirectedEdgeMode(); // Nuevo: Resetea el modo de arista dirigida
        graphPanel.setDeleteMode(false);
        
        graph.clear();
        nodeCounter = 1;
        isGameActive = false;
        currentDelayMillis = 500; // Reiniciar el retraso a predeterminado
        
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        edgeSimpleInput.setText("Clic en 2 nodos..."); // Restablecer texto de arista simple
        // edgeDirectedInput ya no existe, el spinner maneja su valor predeterminado
        initialNodeInput.setText("");
        messageArea.setText("");
        
        showMessage("Aplicación reiniciada. Puedes crear un nuevo grafo.", "Info");
        setControlsEnabled(true);
    }

    private void showMessage(final String msg, final String type) {
        final String prefix; 
        switch (type) {
            case "Error":
                prefix = "[ERROR] ";
                break;
            case "Warning":
                prefix = "[ADVERTENCIA] ";
                break;
            case "Success":
                prefix = "[ÉXITO] ";
                break;
            case "Info":
            default:
                prefix = "[INFO] ";
                break;
        }

        SwingUtilities.invokeLater(() -> {
            messageArea.append(prefix + msg + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    KostantGameGUI app = new KostantGameGUI();
                    app.setVisible(true);
                }
                catch (Exception e) {
                    System.err.println("Error durante la inicialización de la GUI: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        catch (Exception e) {
            System.err.println("Error crítico al iniciar la aplicación Swing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
