package com.oryxos.tool.sandbox;

/**
 * 沙箱动作描述值对象.
 *
 * @param type 动作类型
 * @param target 操作目标（如文件路径、命令、URL等）
 * @author OryxOS Team
 */
public record SandboxAction(ActionType type, String target) {}
