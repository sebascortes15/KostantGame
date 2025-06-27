import javax.swing.*;
import javax.swing.event.DocumentEvent; 
import javax.swing.event.DocumentListener; 
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.math.BigInteger;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
        return weight == edge.weight &&
               Objects.equals(source, edge.source) &&
               Objects.equals(target, edge.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, weight);
    }

    @Override
    public String toString() {
        return source + "->" + target + ":" + weight;
    }
}

class Graph {
    private final Map<String, BigInteger> nodesChips;
    private final Map<String, Map<String, Integer>> allDirectedEdges;
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
            nodesChips.put(nodeName, BigInteger.ZERO);
            allDirectedEdges.put(nodeName, new ConcurrentHashMap<>());
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
        nodesChips.put(newSpecialNodeLogicalName, BigInteger.ONE);
        allDirectedEdges.put(newSpecialNodeLogicalName, new ConcurrentHashMap<>());

        addDirectedEdgeInternal(newSpecialNodeLogicalName, targetNodeLogicalName, 1, true); 
        System.out.println("Nodo especial '" + newSpecialNodeLogicalName + "' añadido, conectado directamente a '" + targetNodeLogicalName + "'.");
        return newSpecialNodeLogicalName;
    }

    private void addDirectedEdgeInternal(String source, String target, int weight, boolean isExplicit) {
        if (!nodesChips.containsKey(source) || !nodesChips.containsKey(target)) {
            throw new IllegalArgumentException("Error: Ambos nodos (origen y destino) deben existir.");
        }
        if (source.equals(target)) {
            System.out.println("Advertencia: No se permiten bucles (aristas a sí mismo).");
            return;
        }

        allDirectedEdges.computeIfAbsent(source, k -> new ConcurrentHashMap<>()).put(target, weight);

        if (isExplicit) {
            explicitEdges.removeIf(e -> e.source.equals(source) && e.target.equals(target));
            explicitEdges.add(new Edge(source, target, weight));
        }
    }

    public void addDirectedEdge(String source, String target, int weight) {
        if (isSpecialNode(target)) {
             throw new IllegalArgumentException("No se pueden añadir aristas dirigidas hacia un nodo especial con este método. Los nodos especiales solo tienen una arista saliente hacia su nodo base.");
        }
        if (isSpecialNode(source) && !target.equals(getSpecialNodeConnectedTarget(source))) {
            throw new IllegalArgumentException("Un nodo especial solo puede tener una arista saliente hacia su nodo base.");
        }

        addDirectedEdgeInternal(source, target, weight, true);

        boolean hasExplicitReverse = false;
        for (Edge e : explicitEdges) {
            if (e.source.equals(target) && e.target.equals(source)) {
                hasExplicitReverse = true;
                break;
            }
        }

        if (!hasExplicitReverse) {
            addDirectedEdgeInternal(target, source, 1, false);
            System.out.println("Arista dirigida '" + source + "' -> '" + target + "' con peso " + weight + " añadida. Inversa implícita (peso 1) también considerada.");
        } else {
             System.out.println("Arista dirigida '" + source + "' -> '" + target + "' con peso " + weight + " añadida. La inversa ya existe explícitamente.");
        }
    }

    public void addUndirectedEdge(String node1, String node2) {
        if (isSpecialNode(node1) || isSpecialNode(node2)) {
            throw new IllegalArgumentException("No se pueden añadir aristas no dirigidas hacia/desde nodos especiales con este método.");
        }
        addDirectedEdge(node1, node2, 1);
        addDirectedEdge(node2, node1, 1);
        System.out.println("Arista no dirigida añadida entre '" + node1 + "' y '" + node2 + "'.");
    }

    public BigInteger getChips(String nodeName) {
        return nodesChips.getOrDefault(nodeName, BigInteger.ZERO);
    }

    public void setChips(String nodeName, BigInteger chips) {
        if (nodesChips.containsKey(nodeName)) {
            nodesChips.put(nodeName, chips);
        } else {
            System.out.println("Error: No se pueden establecer chips para un nodo inexistente: '" + nodeName + "'.");
        }
    }

    public Map<String, Integer> getIncomingEdgesWithWeights(String nodeName) {
        Map<String, Integer> incoming = new ConcurrentHashMap<>();
        for (String sourceNode : allDirectedEdges.keySet()) {
            if (allDirectedEdges.get(sourceNode).containsKey(nodeName)) {
                incoming.put(sourceNode, allDirectedEdges.get(sourceNode).get(nodeName));
            }
        }
        return incoming;
    }

    public int getEdgeWeight(String source, String target) {
        return allDirectedEdges.getOrDefault(source, Collections.emptyMap()).getOrDefault(target, 0);
    }

    public List<String> getAllNodes() {
        return new ArrayList<>(nodesChips.keySet());
    }

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

        if (!isSpecialNode(nodeName)) {
            String associatedSpecialNode = nodeName + "'";
            if (isSpecialNode(associatedSpecialNode)) {
                removeNode(associatedSpecialNode);
            }
        }
        
        allDirectedEdges.remove(nodeName); 
        explicitEdges.removeIf(e -> e.source.equals(nodeName) || e.target.equals(nodeName));

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

    private BigInteger getNeighborsSum(String node) {
        BigInteger sum = BigInteger.ZERO;
        for (Map.Entry<String, Integer> entry : graph.getIncomingEdgesWithWeights(node).entrySet()) {
            String sourceNode = entry.getKey();
            int weight = entry.getValue();
            sum = sum.add(graph.getChips(sourceNode).multiply(BigInteger.valueOf(weight))); 
        }
        return sum;
    }

    public String getNodeState(String node) {
        if (graph.isSpecialNode(node)) {
            return "Happy";
        }

        BigInteger c_i = graph.getChips(node);
        BigInteger sum_neighbors = getNeighborsSum(node);
        
        BigInteger two_c_i = c_i.multiply(BigInteger.TWO);

        if (two_c_i.compareTo(sum_neighbors) < 0) {
            return "Sad";
        } else if (two_c_i.compareTo(sum_neighbors) > 0) {
            return "Excited";
        } else {
            return "Happy";
        }
    }

    public void initializeGame(String specifiedInitialNodeLogicalName) {
        for (String node : graph.getAllNodes()) {
            graph.setChips(node, BigInteger.ZERO);
        }

        if (!graph.getSpecialNodesLogicalNames().isEmpty()) {
            for (String specialNode : graph.getSpecialNodesLogicalNames()) {
                graph.setChips(specialNode, BigInteger.ONE);
                System.out.println("Juego modificado inicializado. El nodo especial '" + specialNode + "' tiene 1 chip.");
            }
        } else {
            if (!graph.getAllNodes().contains(specifiedInitialNodeLogicalName)) {
                throw new IllegalArgumentException("El nodo inicial '" + specifiedInitialNodeLogicalName + "' no existe en el grafo.");
            }
            graph.setChips(specifiedInitialNodeLogicalName, BigInteger.ONE);
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

    public BigInteger performReflection(String nodeToReflect) {
        if (!graph.getAllNodes().contains(nodeToReflect)) {
            throw new IllegalArgumentException("El nodo '" + nodeToReflect + "' no existe en el grafo.");
        }
        if (graph.isSpecialNode(nodeToReflect)) {
            throw new IllegalStateException("El nodo especial es siempre feliz y no puede ser reflejado.");
        }
        if (!getNodeState(nodeToReflect).equals("Sad")) {
            throw new IllegalStateException("El nodo '" + nodeToReflect + "' no está triste y no puede ser reflejado.");
        }

        BigInteger old_c_i = graph.getChips(nodeToReflect);
        BigInteger sum_neighbors_c_j = getNeighborsSum(nodeToReflect);
        
        BigInteger new_c_i = old_c_i.negate().add(sum_neighbors_c_j);
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
                break;
            }

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
    private String firstNodeSelectedForEdge = null;
    private boolean isSimpleEdgeMode = false;
    private boolean isDirectedEdgeMode = false;
    private int currentDirectedEdgeWeight = 1;
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
                        if (distance < NODE_SIZE * 0.9) {
                            nodePlacementListener.onPlacementError("Ya hay un nodo muy cerca de esa posición. Elige otro lugar.");
                            return;
                        }
                    }

                    graph.addNode(pendingNodeName);
                    
                    nodePositions.put(pendingNodeName, new Point(x, y));
                    repaint();
                    nodePlacementListener.onNodePlaced(pendingNodeName);
                } else if (isSimpleEdgeMode) {
                    String clickedNode = getNodeAt(e.getPoint());

                    if (clickedNode == null) {
                        edgeCreationListener.onEdgeCreationCancelled("Clic fuera de un nodo. Conexión de aristas simples cancelada.");
                        resetSimpleEdgeMode();
                        return;
                    }
                    
                    if (graph.isSpecialNode(clickedNode)) {
                         edgeCreationListener.onEdgeCreationError("No se pueden añadir aristas no dirigidas hacia/desde un nodo especial.");
                         resetSimpleEdgeMode();
                         return;
                    }

                    if (firstNodeSelectedForEdge == null) {
                        firstNodeSelectedForEdge = clickedNode;
                        edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, null, true, false);
                        repaint();
                    } else {
                        if (clickedNode.equals(firstNodeSelectedForEdge)) {
                            edgeCreationListener.onEdgeCreationError("No puedes conectar un nodo consigo mismo.");
                            firstNodeSelectedForEdge = null;
                            repaint();
                        } else {
                            try {
                                graph.addUndirectedEdge(firstNodeSelectedForEdge, clickedNode); 
                                edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, clickedNode, true, false);
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
                } else if (isDirectedEdgeMode) {
                    String clickedNode = getNodeAt(e.getPoint());

                    if (clickedNode == null) {
                        edgeCreationListener.onEdgeCreationCancelled("Clic fuera de un nodo. Conexión de aristas dirigidas cancelada.");
                        resetDirectedEdgeMode();
                        return;
                    }

                    if (graph.isSpecialNode(clickedNode)) {
                        edgeCreationListener.onEdgeCreationError("No se pueden añadir aristas dirigidas hacia un nodo especial con este método.");
                        resetDirectedEdgeMode();
                        return;
                    }

                    if (firstNodeSelectedForEdge == null) {
                        firstNodeSelectedForEdge = clickedNode;
                        edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, null, false, true);
                        repaint();
                    } else {
                        if (clickedNode.equals(firstNodeSelectedForEdge)) {
                            edgeCreationListener.onEdgeCreationError("No puedes conectar un nodo consigo mismo.");
                            firstNodeSelectedForEdge = null;
                            repaint();
                        } else {
                            try {
                                graph.addDirectedEdge(firstNodeSelectedForEdge, clickedNode, currentDirectedEdgeWeight); 
                                edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, clickedNode, false, true);
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
                } else {
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

    public void startSimpleEdgeMode() {
        this.isSimpleEdgeMode = true;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public void resetSimpleEdgeMode() {
        this.isSimpleEdgeMode = false;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }
    
    public boolean isSimpleEdgeMode() {
        return isSimpleEdgeMode;
    }

    public void startDirectedEdgeMode(int weight) {
        this.isDirectedEdgeMode = true;
        this.currentDirectedEdgeWeight = weight;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public void resetDirectedEdgeMode() {
        this.isDirectedEdgeMode = false;
        this.currentDirectedEdgeWeight = 1;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public boolean isDirectedEdgeMode() {
        return isDirectedEdgeMode;
    }

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

        Set<String> drawnUndirectedPairs = new HashSet<>();

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(2));
        for (Edge edge : graph.getExplicitEdges()) {
            Point p1 = nodePositions.get(edge.source);
            Point p2 = nodePositions.get(edge.target);
            
            if (p1 != null && p2 != null) {
                boolean hasExplicitReverseWeight1 = false;
                for (Edge reverseEdge : graph.getExplicitEdges()) {
                    if (reverseEdge.source.equals(edge.target) && 
                        reverseEdge.target.equals(edge.source) && 
                        reverseEdge.weight == 1 &&
                        edge.weight == 1) {
                        hasExplicitReverseWeight1 = true;
                        break;
                    }
                }
                
                boolean isVisuallyUndirected = hasExplicitReverseWeight1 &&
                                                !graph.isSpecialNode(edge.source) && 
                                                !graph.isSpecialNode(edge.target);

                String undirectedPairKey = (edge.source.compareTo(edge.target) < 0) ? 
                                           (edge.source + "-" + edge.target) : 
                                           (edge.target + "-" + edge.source);

                if (isVisuallyUndirected && !drawnUndirectedPairs.contains(undirectedPairKey)) {
                    drawSimpleLine(g2d, p1.x, p1.y, p2.x, p2.y);
                    drawnUndirectedPairs.add(undirectedPairKey);
                } else if (!isVisuallyUndirected) { 
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
                baseColor = Color.RED.darker();
            } else {
                String state = game.getNodeState(nodeName);
                switch (state) {
                    case "Happy":
                        baseColor = new Color(102, 204, 102);
                        break;
                    case "Sad":
                        baseColor = Color.BLUE.darker();
                        break;
                    case "Excited":
                        baseColor = Color.GREEN.darker();
                        break;
                    default:
                        baseColor = Color.GRAY;
                        break;
                }
            }

            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.fillOval(p.x - NODE_SIZE / 2 + 5, p.y - NODE_SIZE / 2 + 5, NODE_SIZE, NODE_SIZE);

            Color brighterColor = baseColor.brighter().brighter();
            Color darkerColor = baseColor.darker().darker();
            
            GradientPaint gradient = new GradientPaint(
                p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, brighterColor,
                p.x + NODE_SIZE / 2, p.y + NODE_SIZE / 2, darkerColor);
            g2d.setPaint(gradient);
            g2d.fillOval(p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, NODE_SIZE, NODE_SIZE);

            if ((isSimpleEdgeMode || isDirectedEdgeMode) && nodeName.equals(firstNodeSelectedForEdge)) {
                g2d.setColor(Color.CYAN);
                g2d.setStroke(new BasicStroke(4));
                g2d.drawOval(p.x - NODE_SIZE / 2 - 2, p.y - NODE_SIZE / 2 - 2, NODE_SIZE + 4, NODE_SIZE + 4);
            } else if (isDeleteMode) {
                g2d.setColor(Color.RED);
                g2d.setStroke(new BasicStroke(4));
                g2d.drawOval(p.x - NODE_SIZE / 2 - 2, p.y - NODE_SIZE / 2 - 2, NODE_SIZE + 4, NODE_SIZE + 4);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawOval(p.x - NODE_SIZE / 2, p.y - NODE_SIZE / 2, NODE_SIZE, NODE_SIZE);
            }


            g2d.setColor(Color.WHITE);
            
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
    
    private void drawSimpleLine(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        g2d.drawLine(x1, y1, x2, y2);
    }

    private void drawArrowedLine(Graphics2D g2d, int x1, int y1, int x2, int y2, int weight) {
        int ARR_SIZE = 10;
        
        double angle = Math.atan2(y2 - y1, x2 - x1);
        
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double ratio = (distance - NODE_SIZE / 2.0) / distance;
        
        int adjustedX2 = (int) (x1 + dx * ratio);
        int adjustedY2 = (int) (y1 + dy * ratio);

        g2d.drawLine(x1, y1, adjustedX2, adjustedY2);

        AffineTransform tx = g2d.getTransform();
        g2d.translate(adjustedX2, adjustedY2);
        g2d.rotate(angle);
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 0);
        arrowHead.addPoint(-ARR_SIZE, ARR_SIZE / 2);
        arrowHead.addPoint(-ARR_SIZE, -ARR_SIZE / 2);
        g2d.fill(arrowHead);
        g2d.setTransform(tx);

        if (weight > 1) {
            String weightText = String.valueOf(weight);
            int midX = (x1 + adjustedX2) / 2;
            int midY = (y1 + adjustedY2) / 2;
            
            double perpendicularAngle = angle + Math.PI / 2;
            int offsetX = (int) (10 * Math.cos(perpendicularAngle));
            int offsetY = (int) (10 * Math.sin(perpendicularAngle));

            g2d.setColor(Color.WHITE);
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
    private final JTextField edgeSimpleInput;
    private final JTextField initialNodeInput;
    private final JTextArea messageArea;
    
    private final JButton toggleNodeModeButton;
    private final JButton toggleSimpleEdgeModeButton;
    private final JButton toggleDirectedEdgeModeButton;
    private final JSpinner directedEdgeWeightSpinner;
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

        SpinnerModel weightModel = new SpinnerNumberModel(1, 1, 10, 1);
        directedEdgeWeightSpinner = new JSpinner(weightModel);
        directedEdgeWeightSpinner.setMaximumSize(new Dimension(80, directedEdgeWeightSpinner.getPreferredSize().height));
        ((JSpinner.DefaultEditor) directedEdgeWeightSpinner.getEditor()).getTextField().setEditable(false);
        directedEdgeInputPanel.add(directedEdgeWeightSpinner);
        directedEdgeInputPanel.add(Box.createHorizontalGlue());
        
        controlPanel.add(directedEdgeInputPanel);
        controlPanel.add(Box.createVerticalStrut(5));
        
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
        specialNodePanel.add(targetNodeSelector);

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
        initialNodeInput.setToolTipText("Ingrese el índice numérico del nodo, ej. 1 para α₁.");
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
            if (graphPanel.isSimpleEdgeMode()) {
                toggleSimpleEdgeMode();
            }
            if (graphPanel.isDirectedEdgeMode()) {
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
            toggleSimpleEdgeModeButton.setEnabled(true);
            toggleDirectedEdgeModeButton.setEnabled(true);
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
            if (graphPanel.isDirectedEdgeMode()) {
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
            if (graphPanel.isSimpleEdgeMode()) {
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
                showMessage("Arista dirigida '" + graphPanel.getNodeDisplayString(sourceNode) + "' -> '" + graphPanel.getNodeDisplayString(targetNode) + "' (Peso: " + graphPanel.getCurrentDirectedEdgeWeight() + ") creada.", "Success");
            }
        }
        if (!graph.getExplicitEdges().isEmpty()) {
            initializeGameButton.setEnabled(true);
        }
        setControlsEnabled(true);
    }


    @Override
    public void onEdgeCreationCancelled(String message) {
        showMessage(message, "Warning");
        setControlsEnabled(true);
        edgeSimpleInput.setText("Clic en 2 nodos...");
        toggleSimpleEdgeModeButton.setText("Activar Modo Arista Simple");
        toggleDirectedEdgeModeButton.setText("Activar Modo Arista Dirigida");
    }

    @Override
    public void onEdgeCreationError(String message) {
        showMessage("Error al crear arista: " + message, "Error");
        edgeSimpleInput.setText("Clic en 2 nodos...");
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
            if (graphPanel.isSimpleEdgeMode()) {
                toggleSimpleEdgeMode();
            }
            if (graphPanel.isDirectedEdgeMode()) {
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
                newSpecialNodePos = new Point(targetPos.x, targetPos.y - (graphPanel.getNodeSize() + 10));
            } else {
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
        } else if (!graphPanel.isNodeMode() && !graphPanel.isSimpleEdgeMode() && !graphPanel.isDirectedEdgeMode() && !graphPanel.isDeleteMode() && isGameActive) {
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
            currentDelayMillis = Math.max(MIN_DELAY_MILLIS, currentDelayMillis / 2);
            showMessage("Juego automático acelerado. Retraso: " + currentDelayMillis + "ms", "Info");
            return;
        }

        setControlsEnabled(false); 
        newGraphButton.setEnabled(true); 
        resetGameConfigButton.setEnabled(true);
        stopAutoPlayButton.setEnabled(true); 

        showMessage("Iniciando juego automático... Por favor, espera.", "Info"); 
        
        currentDelayMillis = 500; 

        autoPlayWorker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int stepsTaken = 0;
                int maxSteps = 500;

                while (stepsTaken < maxSteps) {
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
                        publish("");
                        
                        if (isCancelled()) {
                            break;
                        }
                        Thread.sleep(currentDelayMillis);
                    }
                    catch (IllegalStateException e) {
                        publish("Error inesperado durante la reflexión automática: " + e.getMessage());
                        break;
                    }
                    catch (InterruptedException e) {
                        publish("Juego automático interrumpido.");
                        Thread.currentThread().interrupt();
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
                for (String messageChunk : chunks) {
                    if (!messageChunk.isEmpty()) {
                        showMessage(messageChunk, "Info");
                    }
                }
                graphPanel.updateGraphDisplay();
            }

            @Override
            protected void done() {
                autoPlayWorker = null;
                try {
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
                    setControlsEnabled(true);
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
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
        }

        for (String node : graph.getAllNodes()) {
            graph.setChips(node, BigInteger.ZERO);
        }
        for (String specialNode : graph.getSpecialNodesLogicalNames()) {
            graph.setChips(specialNode, BigInteger.ONE);
        }

        isGameActive = false;
        graphPanel.updateGraphDisplay();
        showMessage("Configuración del juego reiniciada. Los chips han vuelto a su estado inicial. Puedes inicializar el juego de nuevo.", "Info");
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        if (graphPanel.isNodeMode()) {
            toggleNodeModeButton.setEnabled(true);
            toggleNodeModeButton.setText("Cancelar Modo Nodo");
            toggleSimpleEdgeModeButton.setEnabled(false); 
            toggleDirectedEdgeModeButton.setEnabled(false);
            directedEdgeWeightSpinner.setEnabled(false);
            toggleDeleteModeButton.setEnabled(false);
            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
            initialNodeInput.setEnabled(false);
            resetGameConfigButton.setEnabled(false); 
        } else if (graphPanel.isSimpleEdgeMode()) {
            toggleSimpleEdgeModeButton.setEnabled(true); 
            toggleSimpleEdgeModeButton.setText("Cancelar Modo Arista Simple"); 
            toggleNodeModeButton.setEnabled(false);
            toggleDirectedEdgeModeButton.setEnabled(false);
            directedEdgeWeightSpinner.setEnabled(false);
            toggleDeleteModeButton.setEnabled(false);
            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            edgeSimpleInput.setText("Selecciona " + (graphPanel.getFirstNodeSelectedForEdge() == null ? "el PRIMER" : "el SEGUNDO") + " nodo...");
            initialNodeInput.setEnabled(false); 
            resetGameConfigButton.setEnabled(false);
        } else if (graphPanel.isDirectedEdgeMode()) {
            toggleDirectedEdgeModeButton.setEnabled(true); 
            toggleDirectedEdgeModeButton.setText("Cancelar Modo Arista Dirigida"); 
            toggleNodeModeButton.setEnabled(false);
            toggleSimpleEdgeModeButton.setEnabled(false);
            toggleDeleteModeButton.setEnabled(false);
            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            initialNodeInput.setEnabled(false); 
            resetGameConfigButton.setEnabled(false);
            directedEdgeWeightSpinner.setEnabled(false);
        } else if (graphPanel.isDeleteMode()) {
            toggleDeleteModeButton.setEnabled(true);
            toggleDeleteModeButton.setText("Cancelar Eliminación");
            toggleNodeModeButton.setEnabled(false);
            toggleSimpleEdgeModeButton.setEnabled(false);
            toggleDirectedEdgeModeButton.setEnabled(false);
            directedEdgeWeightSpinner.setEnabled(false);
            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            initialNodeInput.setEnabled(false);
            resetGameConfigButton.setEnabled(false);
        } 
        else {
            toggleNodeModeButton.setEnabled(enabled);
            toggleNodeModeButton.setText("Activar Modo Nodo");
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));

            toggleSimpleEdgeModeButton.setEnabled(enabled && graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2); 
            toggleSimpleEdgeModeButton.setText("Activar Modo Arista Simple"); 
            edgeSimpleInput.setText("Clic en 2 nodos...");
            
            toggleDirectedEdgeModeButton.setEnabled(enabled && graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2);
            toggleDirectedEdgeModeButton.setText("Activar Modo Arista Dirigida");
            directedEdgeWeightSpinner.setEnabled(enabled);
            
            toggleDeleteModeButton.setEnabled(enabled && !graph.getAllNodes().isEmpty());
            toggleDeleteModeButton.setText("Eliminar Nodo");

            boolean hasRegularNodes = graph.getAllNodes().stream().anyMatch(node -> !graph.isSpecialNode(node));
            
            boolean canAddMoreSpecialNodes = false;
            if (hasRegularNodes) {
                for (String nodeLogicalName : graph.getAllNodes()) {
                    if (!graph.isSpecialNode(nodeLogicalName)) {
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
                targetNodeSelector.removeAllItems();
            }


            if (isGameActive) {
                initializeGameButton.setEnabled(false);
                initialNodeInput.setEnabled(false);
                boolean hasUnhappyNodes = !game.getUnhappyNodes().isEmpty();
                reflectButton.setEnabled(hasUnhappyNodes);
                playFullButton.setEnabled(hasUnhappyNodes);
                stopAutoPlayButton.setEnabled(autoPlayWorker != null && !autoPlayWorker.isDone());
                toggleNodeModeButton.setEnabled(false); 
                toggleSimpleEdgeModeButton.setEnabled(false);
                toggleDirectedEdgeModeButton.setEnabled(false);
                directedEdgeWeightSpinner.setEnabled(false);
                toggleDeleteModeButton.setEnabled(false);
                addSpecialNodeButton.setEnabled(false);
                targetNodeSelector.setEnabled(false);
                resetGameConfigButton.setEnabled(true);
            } else {
                boolean hasAnyNodes = !graph.getAllNodes().isEmpty();
                boolean hasSpecialNodes = !graph.getSpecialNodesLogicalNames().isEmpty();
                boolean hasExplicitEdges = !graph.getExplicitEdges().isEmpty();

                initializeGameButton.setEnabled(enabled && (hasSpecialNodes || (hasAnyNodes && !initialNodeInput.getText().trim().isEmpty() && hasExplicitEdges)));
                
                initialNodeInput.setEnabled(enabled && !hasSpecialNodes && hasAnyNodes);

                reflectButton.setEnabled(false);
                playFullButton.setEnabled(false);
                stopAutoPlayButton.setEnabled(false);
                resetGameConfigButton.setEnabled(enabled && hasAnyNodes);
            }
        }
        
        newGraphButton.setEnabled(true);
    }

    private void resetApplication() {
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
        }

        graphPanel.resetNodePositions(); 
        graphPanel.setNodeMode(false);
        graphPanel.resetSimpleEdgeMode(); 
        graphPanel.resetDirectedEdgeMode(); 
        graphPanel.setDeleteMode(false);
        
        graph.clear();
        nodeCounter = 1;
        isGameActive = false;
        currentDelayMillis = 500;
        
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        edgeSimpleInput.setText("Clic en 2 nodos...");
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
                } catch (Exception e) {
                    System.err.println("Error durante la inicialización de la GUI: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("Error crítico al iniciar la aplicación Swing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
