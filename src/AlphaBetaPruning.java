import java.io.*;
import java.util.*;

public class AlphaBetaPruning {

    // 节点类
    static class Node {
        int id;
        int parent;
        int value;
        List<Node> children;
        boolean isMaxNode; // 用于标识是否为MAX节点（偶数层为MAX，0层为根节点层）

        Node(int id, int parent, int value) {
            this.id = id;
            this.parent = parent;
            this.value = value;
            this.children = new ArrayList<>();
            this.isMaxNode = false; // 将在构建树时计算
        }
    }

    // 剪枝记录类
    static class PruneRecord {
        int parentId;
        int childId;
        String type; // "alpha" 或 "beta"

        PruneRecord(int parentId, int childId, String type) {
            this.parentId = parentId;
            this.childId = childId;
            this.type = type;
        }

        @Override
        public String toString() {
            return parentId + " " + childId + " " + type;
        }
    }

    // 结果类
    static class SearchResult {
        int bestMoveFrom;
        int bestMoveTo;
        int bestValue;
        List<PruneRecord> prunedBranches;

        SearchResult(int from, int to, int value) {
            this.bestMoveFrom = from;
            this.bestMoveTo = to;
            this.bestValue = value;
            this.prunedBranches = new ArrayList<>();
        }
    }

    // 读取 tree.txt 文件，构建树并计算节点类型
    static Map<Integer, Node> buildTree(String filename) throws IOException {
        Map<Integer, Node> nodes = new HashMap<>();
        Map<Integer, List<Integer>> childrenMap = new HashMap<>();

        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        boolean isFirstLine = true;

        // 第一遍：创建所有节点
        while ((line = reader.readLine()) != null) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

            // 静默跳过表头行
            if (isFirstLine && (line.contains("结点ID") || line.contains("节点ID"))) {
                isFirstLine = false;
                continue;
            }
            isFirstLine = false;

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3) continue;

            try {
                int id = Integer.parseInt(parts[0]);
                int parent = Integer.parseInt(parts[1]);
                int value = Integer.parseInt(parts[2]);

                Node node = new Node(id, parent, value);
                nodes.put(id, node);

                if (parent != -1) {
                    childrenMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
                }
            } catch (NumberFormatException e) {
                // 静默跳过错误行，不打印警告
                continue;
            }
        }
        reader.close();

        // 第二遍：建立子节点关系
        for (Map.Entry<Integer, List<Integer>> entry : childrenMap.entrySet()) {
            Node parent = nodes.get(entry.getKey());
            if (parent != null) {
                for (int childId : entry.getValue()) {
                    Node child = nodes.get(childId);
                    if (child != null) {
                        parent.children.add(child);
                    }
                }
            }
        }

        // 计算每个节点的类型（根节点为MAX）
        Node root = null;
        for (Node node : nodes.values()) {
            if (node.parent == -1) {
                root = node;
                break;
            }
        }

        if (root != null) {
            calculateNodeTypes(root, 0);
        }

        return nodes;
    }

    // 递归计算节点类型：偶数层为MAX，奇数层为MIN
    static void calculateNodeTypes(Node node, int depth) {
        node.isMaxNode = (depth % 2 == 0);

        for (Node child : node.children) {
            calculateNodeTypes(child, depth + 1);
        }
    }

    // α-β剪枝搜索
    static SearchResult alphaBetaSearch(Node root) {
        SearchResult result = new SearchResult(-1, -1, 0);

        if (root.children.isEmpty()) {
            // 没有子节点，直接返回根节点的值
            result.bestValue = root.value;
            result.bestMoveFrom = root.id;
            result.bestMoveTo = root.id;
            return result;
        }

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestValue = Integer.MIN_VALUE;
        Node bestChild = null;

        // 根节点是MAX节点
        for (Node child : root.children) {
            int value = minValue(child, alpha, beta, result.prunedBranches);

            if (value > bestValue) {
                bestValue = value;
                bestChild = child;
            }

            alpha = Math.max(alpha, bestValue);
        }

        if (bestChild != null) {
            result.bestMoveFrom = root.id;
            result.bestMoveTo = bestChild.id;
            result.bestValue = bestValue;
        }

        return result;
    }

    // MAX节点的值计算
    static int maxValue(Node node, int alpha, int beta, List<PruneRecord> pruned) {
        // 如果是叶子节点，直接返回值
        if (node.children.isEmpty()) {
            return node.value;
        }

        int value = Integer.MIN_VALUE;

        for (int i = 0; i < node.children.size(); i++) {
            Node child = node.children.get(i);
            int childValue = minValue(child, alpha, beta, pruned);
            value = Math.max(value, childValue);

            if (value >= beta) {
                // β剪枝：记录当前节点之后的所有兄弟节点
                for (int j = i + 1; j < node.children.size(); j++) {
                    Node prunedChild = node.children.get(j);
                    pruned.add(new PruneRecord(node.id, prunedChild.id, "beta"));
                }
                return value;
            }

            alpha = Math.max(alpha, value);
        }

        return value;
    }

    // MIN节点的值计算
    static int minValue(Node node, int alpha, int beta, List<PruneRecord> pruned) {
        // 如果是叶子节点，直接返回值
        if (node.children.isEmpty()) {
            return node.value;
        }

        int value = Integer.MAX_VALUE;

        for (int i = 0; i < node.children.size(); i++) {
            Node child = node.children.get(i);
            int childValue = maxValue(child, alpha, beta, pruned);
            value = Math.min(value, childValue);

            if (value <= alpha) {
                // α剪枝：记录当前节点之后的所有兄弟节点
                for (int j = i + 1; j < node.children.size(); j++) {
                    Node prunedChild = node.children.get(j);
                    pruned.add(new PruneRecord(node.id, prunedChild.id, "alpha"));
                }
                return value;
            }

            beta = Math.min(beta, value);
        }

        return value;
    }

    // 按指定格式输出结果
    static void printResults(SearchResult result) {
        // 第一行：最佳走步
        System.out.println(result.bestMoveFrom + " " + result.bestMoveTo + " " + result.bestValue);

        // 后续行：剪枝记录
        for (PruneRecord record : result.prunedBranches) {
            System.out.println(record);
        }
    }

    public static void main(String[] args) {
        try {
            // 1. 读取tree.txt文件
            Map<Integer, Node> nodes = buildTree("tree.txt");

            // 2. 找到根节点
            Node root = null;
            for (Node node : nodes.values()) {
                if (node.parent == -1) {
                    root = node;
                    break;
                }
            }

            if (root == null) {
                System.out.println("未找到根节点！");
                return;
            }

            // 3. 执行α-β搜索
            SearchResult result = alphaBetaSearch(root);

            // 4. 按指定格式输出结果
            printResults(result);

        } catch (IOException e) {
            System.out.println("读取文件出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}