package org.spacebar.escape.j2se;

import java.util.List;

import org.spacebar.escape.j2se.AStarSearch.AStarNode;

public interface ChildrenGenerator {
    public List<AStarNode> generateChildren(AStarNode node, int moveLimit);
}
