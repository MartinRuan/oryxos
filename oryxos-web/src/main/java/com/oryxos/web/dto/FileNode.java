package com.oryxos.web.dto;

import java.util.List;

/**
 * 工作区目录树节点.
 *
 * @author oryxos
 */
public record FileNode(
    String name, String path, NodeType type, boolean readable, List<FileNode> children) {

  /**
   * 节点类型.
   *
   * @author oryxos
   */
  public enum NodeType {
    /** 目录. */
    DIRECTORY,
    /** 普通文件. */
    FILE
  }
}
