package pro.sketchware.utility.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CircularDependencyDetector<T> {
    private final Map<T, List<T>> graph = new HashMap<>();

    public void addNode(T node) {
        graph.computeIfAbsent(node, ignored -> new ArrayList<>());
    }

    public void addDependency(T node, T dependency) {
        addNode(node);
        addNode(dependency);
        graph.get(node).add(dependency);
    }

    public List<List<T>> findCycles() {
        Set<T> visited = new HashSet<>();
        Set<T> visiting = new HashSet<>();
        ArrayDeque<T> stack = new ArrayDeque<>();
        List<List<T>> cycles = new ArrayList<>();
        for (T node : graph.keySet()) dfs(node, visited, visiting, stack, cycles);
        return cycles;
    }

    public boolean hasCycles() { return !findCycles().isEmpty(); }

    private void dfs(T node, Set<T> visited, Set<T> visiting, ArrayDeque<T> stack, List<List<T>> cycles) {
        if (visited.contains(node)) return;
        if (visiting.contains(node)) {
            List<T> cycle = new ArrayList<>();
            for (T item : stack) {
                cycle.add(item);
                if (item.equals(node)) break;
            }
            Collections.reverse(cycle);
            cycle.add(node);
            cycles.add(cycle);
            return;
        }
        visiting.add(node);
        stack.push(node);
        for (T dependency : graph.getOrDefault(node, Collections.emptyList())) {
            dfs(dependency, visited, visiting, stack, cycles);
        }
        stack.pop();
        visiting.remove(node);
        visited.add(node);
    }
}
