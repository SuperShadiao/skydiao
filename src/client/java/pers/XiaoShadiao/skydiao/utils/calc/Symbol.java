package pers.XiaoShadiao.skydiao.utils.calc;

class Symbol implements Stackable {

    public final SymbolType type;
    public final String funcName; // 函数名（仅当type为函数时有效）

    public Symbol(SymbolType type) {
        this.type = type;
        this.funcName = null;
    }

    public Symbol(SymbolType type, String funcName) {
        this.type = type;
        this.funcName = funcName;
    }

    @Override
    public String toString() {
        return type == SymbolType.函数 ? funcName : type.name();
    }
}

enum SymbolType {

    加("+", 0),
    减("-", 0),
    乘("*", 1),
    除("/", 1),
    函数("F", Integer.MAX_VALUE),
    逗号(",", Integer.MIN_VALUE),
    次方("^", Integer.MAX_VALUE),


    左括号("(", -10),
    右括号(")", -10),
    $("NOP", -10);

    private final String s;
    private final int priority;

    SymbolType(String s, int priority) {
        this.s = s;
        this.priority = priority;
    }

    @Override
    public String toString() {
        return s;
    }

    public int getPriority() {
        return priority;
    }
}