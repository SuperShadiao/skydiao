package pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.factory;

import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.AbstractNode;

import java.util.Map;

public abstract class AbstractNodeFactory<T extends AbstractNode> {

    public abstract String getType();

    public abstract T createNode(String id, Map<String, Object> literals);

}
