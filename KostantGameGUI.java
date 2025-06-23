import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class Graph {
    private final Map<String, Integer> nodesChips;
    private final Map<String, Set<String>> adjacencyList;
    private final Set<String> specialNodesLogicalNames;

    public Graph() {
        this.nodesChips = new ConcurrentHashMap<>();
        this.adjacencyList = new ConcurrentHashMap<>();
        this.specialNodesLogicalNames = ConcurrentHashMap.newKeySet();
    }

    public void addNode(String nodeName) {
        if (!nodesChips.containsKey(nodeName)) {
            nodesChips.put(nodeName, 0);
            adjacencyList.put(nodeName, ConcurrentHashMap.newKeySet());
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
        nodesChips.put(newSpecialNodeLogicalName, 1);
        adjacencyList.put(newSpecialNodeLogicalName, ConcurrentHashMap.newKeySet());

        adjacencyList.get(newSpecialNodeLogicalName).add(targetNodeLogicalName);
        adjacencyList.get(targetNodeLogicalName).add(newSpecialNodeLogicalName);
        System.out.println("Nodo especial '" + newSpecialNodeLogicalName + "' añadido, conectado a '" + targetNodeLogicalName + "'.");
        return newSpecialNodeLogicalName;
    }

    public void addEdge(String node1, String node2) {
        if (!nodesChips.containsKey(node1) || !nodesChips.containsKey(node2)) {
            throw new IllegalArgumentException("Error: Ambos nodos deben existir antes de añadir una arista.");
        }
        if (node1.equals(node2)) {
            System.out.println("Advertencia: No se permiten bucles (aristas a sí mismo) en un grafo simple.");
            return;
        }

        if (isSpecialNode(node1) || isSpecialNode(node2)) {
            throw new IllegalArgumentException("No se pueden añadir aristas hacia/desde nodos siempre felices con este método.");
        }

        if (adjacencyList.get(node1).contains(node2)) {
            System.out.println("Advertencia: La arista entre '" + node1 + "' y '" + node2 + "' ya existe.");
            return;
        }

        adjacencyList.get(node1).add(node2);
        adjacencyList.get(node2).add(node1);
        System.out.println("Arista añadida entre '" + node1 + "' y '" + node2 + "' en el grafo lógico.");
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

    public Set<String> getNeighbors(String nodeName) {
        return adjacencyList.getOrDefault(nodeName, Collections.emptySet());
    }

    public List<String> getAllNodes() {
        return new ArrayList<>(nodesChips.keySet());
    }

    public Set<AbstractMap.SimpleEntry<String, String>> getAllEdges() {
        Set<AbstractMap.SimpleEntry<String, String>> edges = new HashSet<>();
        for (String node1 : adjacencyList.keySet()) {
            for (String node2 : adjacencyList.get(node1)) {
                String n1 = node1;
                String n2 = node2;
                if (isSpecialNode(n1) && !isSpecialNode(n2)) {
                } else if (isSpecialNode(n2) && !isSpecialNode(n1)) {
                    String temp = n1;
                    n1 = n2;
                    n2 = temp;
                } else if (n1.compareTo(n2) > 0) {
                    String temp = n1;
                    n1 = n2;
                    n2 = temp;
                }
                edges.add(new AbstractMap.SimpleEntry<>(n1, n2));
            }
        }
        return edges;
    }

    public void clear() {
        nodesChips.clear();
        adjacencyList.clear();
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
        Set<String> neighbors = adjacencyList.get(specialNodeId);
        if (neighbors != null && !neighbors.isEmpty()) {
            return neighbors.iterator().next();
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

        Set<String> neighborsToRemove = adjacencyList.getOrDefault(nodeName, Collections.emptySet());
        for (String neighbor : neighborsToRemove) {
            if (adjacencyList.containsKey(neighbor)) {
                adjacencyList.get(neighbor).remove(nodeName);
            }
        }
        adjacencyList.remove(nodeName);

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

    private int getNeighborsSum(String node) {
        int sum = 0;
        for (String neighbor : graph.getNeighbors(node)) {
            sum += graph.getChips(neighbor);
        }
        return sum;
    }

    public String getNodeState(String node) {
        if (graph.isSpecialNode(node)) {
            return "Happy";
        }

        int c_i = graph.getChips(node);
        int sum_neighbors = getNeighborsSum(node);
        double threshold = 0.5 * sum_neighbors;

        if (c_i < threshold) {
            return "Sad"; // Changed from Unhappy
        } else if (c_i > threshold) {
            return "Excited";
        } else {
            return "Happy";
        }
    }

    public void initializeGame(String specifiedInitialNodeLogicalName) {
        for (String node : graph.getAllNodes()) {
            graph.setChips(node, 0);
        }

        if (!graph.getSpecialNodesLogicalNames().isEmpty()) {
            for (String specialNode : graph.getSpecialNodesLogicalNames()) {
                graph.setChips(specialNode, 1);
                System.out.println("Juego modificado inicializado. El nodo especial '" + specialNode + "' tiene 1 chip.");
            }
        } else {
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
            if (!graph.isSpecialNode(node) && getNodeState(node).equals("Sad")) { // Changed from Unhappy
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
        if (!getNodeState(nodeToReflect).equals("Sad")) { // Changed from Unhappy
            throw new IllegalStateException("El nodo '" + nodeToReflect + "' no está triste y no puede ser reflejado."); // Changed from infeliz
        }

        int old_c_i = graph.getChips(nodeToReflect);
        int sum_neighbors_c_j = getNeighborsSum(nodeToReflect);
        
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
                System.out.println("El juego ha convergido: No quedan nodos tristes."); // Changed from infelices
                break; // El juego ha terminado
            }

            // Estrategia simple: elegir el primer nodo infeliz para la reflexión.
            String nodeToReflect = unhappyNodes.get(0);
            try {
                performReflection(nodeToReflect);
            } catch (IllegalStateException e) {
                // Esto no debería ocurrir si getUnhappyNodes funciona correctamente.
                System.err.println("Error inesperado durante la reflexión automática: " + e.getMessage());
                break;
            }
            stepsTaken++;
        }

        if (stepsTaken >= maxSteps) {
            System.out.println("El juego se detuvo después de " + maxSteps + " pasos (límite alcanzado). Todavía pueden quedar nodos tristes."); // Changed from infelices
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
    void onEdgeCreated(String node1, String node2);
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
    private boolean isEdgeMode = false;
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
                } else if (isEdgeMode) {
                    String clickedNode = getNodeAt(e.getPoint());

                    if (clickedNode == null) {
                        edgeCreationListener.onEdgeCreationCancelled("Clic fuera de un nodo. Conexión de aristas cancelada.");
                        resetEdgeMode();
                        return;
                    }
                    
                    if (graph.isSpecialNode(clickedNode)) {
                        edgeCreationListener.onEdgeCreationError("No se pueden añadir aristas al nodo especial en este modo, ya tiene su conexión inicial.");
                        resetEdgeMode();
                        return;
                    }


                    if (firstNodeSelectedForEdge == null) {
                        firstNodeSelectedForEdge = clickedNode;
                        edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, null);
                        repaint();
                    } else {
                        if (clickedNode.equals(firstNodeSelectedForEdge)) {
                            edgeCreationListener.onEdgeCreationError("No puedes conectar un nodo consigo mismo.");
                            firstNodeSelectedForEdge = null;
                            repaint();
                        } else {
                            try {
                                graph.addEdge(firstNodeSelectedForEdge, clickedNode);
                                edgeCreationListener.onEdgeCreated(firstNodeSelectedForEdge, clickedNode);
                                firstNodeSelectedForEdge = null;
                                repaint();
                            } catch (IllegalArgumentException ex) {
                                edgeCreationListener.onEdgeCreationError(ex.getMessage());
                                firstNodeSelectedForEdge = null;
                                repaint();
                            }
                        }
                    }
                } else if (isDeleteMode) {
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

    public void startEdgeMode() {
        this.isEdgeMode = true;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }

    public void resetEdgeMode() {
        this.isEdgeMode = false;
        this.firstNodeSelectedForEdge = null;
        repaint();
    }
    
    public boolean isEdgeMode() {
        return isEdgeMode;
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
            } catch (NumberFormatException e) {
                return "α" + logicalNodeName + "'";
            }
        }
        try {
            String subscriptPart = convertToSubscript(logicalNodeName);
            return "α" + subscriptPart;
        } catch (NumberFormatException e) {
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

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(2));
        for (AbstractMap.SimpleEntry<String, String> edge : graph.getAllEdges()) {
            String node1Name = edge.getKey();
            String node2Name = edge.getValue();

            Point p1 = nodePositions.get(node1Name);
            Point p2 = nodePositions.get(node2Name);
            
            if (p1 != null && p2 != null) {
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
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
                    case "Sad": // Changed from Unhappy
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

            if (isEdgeMode && nodeName.equals(firstNodeSelectedForEdge)) {
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

            // Adjust vertical positioning for both texts
            int nodeTextX = p.x - nodeTextWidth / 2;
            // Position node name slightly above center
            int nodeTextY = p.y - fm.getHeight() / 2 + fm.getAscent() / 2 - (fm.getAscent() / 2 + 5); 

            int chipsTextX = p.x - chipsTextWidth / 2;
            // Position chip count slightly below center
            int chipsTextY = p.y + fm.getHeight() / 2 + fm.getAscent() / 2 - (fm.getAscent() / 2 - 10); 

            g2d.drawString(displayedNodeName, nodeTextX, nodeTextY);
            g2d.drawString(chipsText, chipsTextX, chipsTextY);
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
    private final JTextField edgeInput;
    private final JTextField initialNodeInput;
    private final JTextArea messageArea;
    
    private final JButton toggleNodeModeButton;
    private final JButton toggleEdgeModeButton;
    private final JButton toggleDeleteModeButton;
    private final JButton initializeGameButton;
    private final JButton reflectButton;
    private final JButton playFullButton; 
    private final JButton stopAutoPlayButton; // New button for stopping automatic play
    private final JButton newGraphButton;
    private final JButton resetGameConfigButton;

    private JComboBox<String> targetNodeSelector;
    private JButton addSpecialNodeButton;

    private int nodeCounter = 1;
    private boolean isGameActive = false;
    private SwingWorker<Integer, String> autoPlayWorker; 
    private volatile int currentDelayMillis = 500; // Default delay: 0.5 seconds
    private final int MIN_DELAY_MILLIS = 50; // Minimum delay for acceleration

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

        JLabel edgeLabel = new JLabel("Añadir Arista (Clic en 2 nodos)");
        edgeLabel.setForeground(Color.WHITE);
        controlPanel.add(edgeLabel);
        edgeInput = new JTextField("Clic para conectar..."); 
        edgeInput.setEditable(false);
        edgeInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, edgeInput.getPreferredSize().height));
        toggleEdgeModeButton = new JButton("Activar Modo Arista");
        toggleEdgeModeButton.setBackground(new Color(70, 130, 180));
        toggleEdgeModeButton.setForeground(Color.WHITE);
        toggleEdgeModeButton.setFocusPainted(false);
        toggleEdgeModeButton.addActionListener(e -> toggleEdgeMode());
        controlPanel.add(edgeInput);
        controlPanel.add(toggleEdgeModeButton);
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
        controlPanel.add(Box.createVerticalStrut(5)); // Reduced strut

        // New Stop Automatic button
        stopAutoPlayButton = new JButton("Detener Automático");
        stopAutoPlayButton.setBackground(new Color(220, 50, 50)); // Red color
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
            if (graphPanel.isEdgeMode()) {
                toggleEdgeMode();
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
            toggleEdgeModeButton.setEnabled(true);
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


    private void toggleEdgeMode() {
        if (graphPanel.isEdgeMode()) {
            graphPanel.resetEdgeMode();
            showMessage("Modo Arista cancelado.", "Info");
            setControlsEnabled(true);
            toggleEdgeModeButton.setText("Activar Modo Arista");
            edgeInput.setText("Clic para conectar...");
        } else {
            if (graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() < 2) {
                showMessage("Necesitas al menos 2 nodos regulares para crear una arista.", "Error");
                return;
            }
            if (graphPanel.isNodeMode()) {
                toggleNodeMode();
            }
            if (graphPanel.isDeleteMode()) {
                toggleDeleteMode();
            }

            graphPanel.startEdgeMode();
            setControlsEnabled(false);
            toggleEdgeModeButton.setEnabled(true);
            toggleEdgeModeButton.setText("Cancelar Modo Arista");
            showMessage("Modo Arista activado. Haz clic en el PRIMER nodo para la conexión.", "Info");
            edgeInput.setText("Selecciona el primer nodo...");
            newGraphButton.setEnabled(true);
        }
    }

    @Override
    public void onEdgeCreated(String node1, String node2) {
        if (node2 == null) {
            showMessage("Primer nodo seleccionado: '" + graphPanel.getNodeDisplayString(node1) + "'. Haz clic en el SEGUNDO nodo.", "Info");
            edgeInput.setText("Selecciona el segundo nodo...");
        } else {
            showMessage("Arista creada entre '" + graphPanel.getNodeDisplayString(node1) + "' y '" + graphPanel.getNodeDisplayString(node2) + "'.", "Success");
            graphPanel.repaint();
            edgeInput.setText("Arista creada. Selecciona el PRIMER nodo para la siguiente...");
        }
        if (!graph.getAllEdges().isEmpty()) {
            initializeGameButton.setEnabled(true);
        }
        setControlsEnabled(true);
    }

    @Override
    public void onEdgeCreationCancelled(String message) {
        showMessage(message, "Warning");
        setControlsEnabled(true);
        edgeInput.setText("Clic para conectar...");
        toggleEdgeModeButton.setText("Activar Modo Arista");
    }

    @Override
    public void onEdgeCreationError(String message) {
        showMessage("Error al crear arista: " + message, "Error");
        edgeInput.setText("Clic para conectar...");
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
            if (graphPanel.isEdgeMode()) {
                toggleEdgeMode();
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
        
        // Separamos la declaración de la asignación para una mejor compatibilidad
        String targetLogicalName; 
        targetLogicalName = convertSubscriptToNormal(selectedDisplayNode); 

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
        } catch (IllegalStateException | IllegalArgumentException e) {
            showMessage("Error al añadir nodo especial: " + e.getMessage(), "Error");
        }
    }

    // This method is now public to be accessible from GraphPanel
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
        } else if (!graphPanel.isNodeMode() && !graphPanel.isEdgeMode() && isGameActive) {
            if (graph.isSpecialNode(nodeLogicalName)) {
                showMessage("El nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' es un nodo siempre feliz y no puede ser reflejado.", "Warning");
            } else if (game.getNodeState(nodeLogicalName).equals("Sad")) { // Changed from Unhappy
                try {
                    game.performReflection(nodeLogicalName);
                    graphPanel.updateGraphDisplay();
                    showMessage("Reflexión realizada en el nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' por clic.", "Info");
                    setControlsEnabled(true);
                } catch (IllegalStateException e) {
                    showMessage("Error de juego al reflejar: " + e.getMessage(), "Error");
                }
            } else {
                showMessage("El nodo '" + graphPanel.getNodeDisplayString(nodeLogicalName) + "' no está triste y no puede ser reflejado.", "Warning"); // Changed from infeliz
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
        } catch (IllegalArgumentException e) {
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
            showMessage("No hay nodos tristes para reflejar. El juego ha convergido.", "Info"); // Changed from infelices
            return;
        }
        
        String nodeToReflect = unhappyNodes.get(0); 
        try {
            game.performReflection(nodeToReflect);
            graphPanel.updateGraphDisplay();
            showMessage("Reflexión realizada en el nodo '" + graphPanel.getNodeDisplayString(nodeToReflect) + "'.", "Info");
            setControlsEnabled(true); 
        } catch (IllegalStateException e) {
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
            // If already running, accelerate it
            currentDelayMillis = Math.max(MIN_DELAY_MILLIS, currentDelayMillis / 2);
            showMessage("Juego automático acelerado. Retraso: " + currentDelayMillis + "ms", "Info");
            return; // Don't start a new worker, just adjust speed
        }

        // Disable controls while the auto-play game runs
        setControlsEnabled(false); 
        newGraphButton.setEnabled(true); // Keep these always enabled
        resetGameConfigButton.setEnabled(true);
        stopAutoPlayButton.setEnabled(true); // Enable stop button

        showMessage("Iniciando juego automático... Por favor, espera.", "Info"); 
        
        // Reset delay when starting a new auto-play session
        currentDelayMillis = 500; 

        autoPlayWorker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int stepsTaken = 0;
                int maxSteps = 500; // Limit to prevent infinite loops

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
                        publish(""); // Trigger UI repaint
                        
                        if (isCancelled()) {
                            break;
                        }
                        Thread.sleep(currentDelayMillis); // Use dynamic delay
                    } catch (IllegalStateException e) {
                        publish("Error inesperado durante la reflexión automática: " + e.getMessage());
                        break;
                    } catch (InterruptedException e) {
                        publish("Juego automático interrumpido.");
                        Thread.currentThread().interrupt(); // Restore the interrupted status
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
                // This method runs on the Event Dispatch Thread (EDT)
                for (String messageChunk : chunks) {
                    if (!messageChunk.isEmpty()) {
                        showMessage(messageChunk, "Info");
                    }
                }
                graphPanel.updateGraphDisplay(); // Update display after each step
            }

            @Override
            protected void done() {
                autoPlayWorker = null; // Clear the worker reference
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
                    setControlsEnabled(true); // Re-enable all controls
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
        // Cancel the autoPlayWorker if it's running
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
        // Disable all creation/deletion/game setup controls if a mode is active
        if (graphPanel.isNodeMode() || graphPanel.isEdgeMode() || graphPanel.isDeleteMode()) {
            toggleNodeModeButton.setEnabled(graphPanel.isNodeMode());
            toggleNodeModeButton.setText(graphPanel.isNodeMode() ? "Cancelar Modo Nodo" : "Activar Modo Nodo");

            toggleEdgeModeButton.setEnabled(graphPanel.isEdgeMode());
            toggleEdgeModeButton.setText(graphPanel.isEdgeMode() ? "Cancelar Modo Arista" : "Activar Modo Arista");

            toggleDeleteModeButton.setEnabled(graphPanel.isDeleteMode());
            toggleDeleteModeButton.setText(graphPanel.isDeleteMode() ? "Cancelar Eliminación" : "Eliminar Nodo");

            initializeGameButton.setEnabled(false);
            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false); // Can't stop auto play if in build/delete mode
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
            initialNodeInput.setEnabled(false);
            resetGameConfigButton.setEnabled(false);
        } 
        else if (isGameActive) { // If game is active
            toggleNodeModeButton.setEnabled(false);
            toggleEdgeModeButton.setEnabled(false);
            toggleDeleteModeButton.setEnabled(false);
            initializeGameButton.setEnabled(false);
            initialNodeInput.setEnabled(false);
            addSpecialNodeButton.setEnabled(false);
            targetNodeSelector.setEnabled(false);

            boolean hasUnhappyNodes = !game.getUnhappyNodes().isEmpty();
            boolean isAutoPlaying = (autoPlayWorker != null && !autoPlayWorker.isDone());

            reflectButton.setEnabled(hasUnhappyNodes && !isAutoPlaying);
            playFullButton.setEnabled(hasUnhappyNodes); // Always enabled to allow acceleration
            stopAutoPlayButton.setEnabled(isAutoPlaying);
            resetGameConfigButton.setEnabled(true); // Always allowed to reset game state
        }
        else { // Default state (no mode active, game not initialized)
            toggleNodeModeButton.setEnabled(enabled);
            toggleNodeModeButton.setText("Activar Modo Nodo");
            nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));

            toggleEdgeModeButton.setEnabled(enabled && graph.getAllNodes().stream().filter(node -> !graph.isSpecialNode(node)).count() >= 2); 
            toggleEdgeModeButton.setText("Activar Modo Arista");
            edgeInput.setText("Clic para conectar...");
            
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
            
            boolean hasAnyNodes = !graph.getAllNodes().isEmpty();
            boolean hasSpecialNodes = !graph.getSpecialNodesLogicalNames().isEmpty();
            initializeGameButton.setEnabled(enabled && (hasSpecialNodes || (hasAnyNodes && !initialNodeInput.getText().trim().isEmpty())));
            initialNodeInput.setEnabled(enabled && !hasSpecialNodes && hasAnyNodes);

            reflectButton.setEnabled(false);
            playFullButton.setEnabled(false);
            stopAutoPlayButton.setEnabled(false);
            resetGameConfigButton.setEnabled(enabled && hasAnyNodes);
        }
        
        newGraphButton.setEnabled(true); // This button is always enabled
    }

    private void resetApplication() {
        // Cancel the autoPlayWorker if it's running
        if (autoPlayWorker != null && !autoPlayWorker.isDone()) {
            autoPlayWorker.cancel(true);
        }

        graphPanel.resetNodePositions();
        graphPanel.setNodeMode(false);
        graphPanel.resetEdgeMode();
        graphPanel.setDeleteMode(false);
        
        graph.clear();
        nodeCounter = 1;
        isGameActive = false;
        currentDelayMillis = 500; // Reset delay to default
        
        nodeInput.setText(graphPanel.getNodeDisplayString(String.valueOf(nodeCounter)));
        edgeInput.setText("Clic para conectar...");
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
