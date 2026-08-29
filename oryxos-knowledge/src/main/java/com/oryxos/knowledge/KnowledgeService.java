package com.oryxos.knowledge;

/**
 * 知识库服务门面契约.
 *
 * @author OryxOS Team
 */
public interface KnowledgeService {

  /**
   * 检索知识条目.
   *
   * @param query 查询关键词
   * @return 检索结果
   */
  String retrieveKnowledge(String query);
}
