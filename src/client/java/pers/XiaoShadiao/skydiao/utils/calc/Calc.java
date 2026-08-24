package pers.XiaoShadiao.skydiao.utils.calc;

import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class Calc {

    private int currentIndex = 0;
    private Stack<Number> numberStack = new Stack<>();
    private Stack<Symbol> operatorStack = new Stack<>();
    public final double result;
    private boolean isFilledZero = false;

    // 函数映射表
    private static final Map<String, FunctionHandler> FUNCTIONS = new HashMap<>();
    static {
        // 单参数函数
        FUNCTIONS.put("lg", args -> {
            if (args.length != 1) throw new RuntimeException("lg函数需要一个参数");
            return Math.log10(args[0]);
        });
        FUNCTIONS.put("ln", args -> {
            if (args.length != 1) throw new RuntimeException("ln函数需要一个参数");
            return Math.log(args[0]);
        });

        // 双参数函数
        FUNCTIONS.put("log", args -> {
            if (args.length != 2) throw new RuntimeException("log函数需要两个参数");
            return Math.log(args[1]) / Math.log(args[0]);
        });
    }

    @FunctionalInterface
    private interface FunctionHandler {
        double apply(double[] args);
    }

    public Calc(String expression) {
        try {
            this.result = evaluate(expression.trim().startsWith("(") ? "1" + expression : expression);
        } catch(Throwable e) {
            System.out.println("NumberStack: " + numberStack.stacks);
            System.out.println("OperatorStack: " + operatorStack.stacks);
            throw new RuntimeException("表达式错误: " + expression.substring(0, currentIndex) + " <-[HERE]", e);
        }
    }

    private double evaluate(String expression) {
        currentIndex = 0;
        operatorStack.push(new Symbol(SymbolType.$)); // 栈底标记

        StringBuilder tokenBuilder = new StringBuilder();
        boolean expectNumber = true;
        boolean lastWasRightParen = false;
        boolean lastWasNumber = false;

        char[] chars = expression.toCharArray();
        while (currentIndex < chars.length) {
            boolean pushedFlag = false;
            char ch = chars[currentIndex];
            currentIndex++;

            if (Character.isWhitespace(ch)) continue;

            // 处理数字和小数点
            if (isDigitOrDecimal(ch)) {
                // 检测非法连续数字 (如"2 3")
                if (!expectNumber && tokenBuilder.length() == 0) {
                    throw new RuntimeException("非法连续数字: " + expression.substring(0, currentIndex) + " <-[HERE]");
                }

                tokenBuilder.append(ch);
                expectNumber = false;
                lastWasRightParen = false;
                lastWasNumber = true;
                continue;
            }

            // 处理字母（函数名）
            if (Character.isLetter(ch)) {
                // 如果tokenBuilder中有数字，先处理数字
                if (tokenBuilder.length() > 0 && Character.isDigit(tokenBuilder.charAt(0))) {
                    String numberToken = tokenBuilder.toString();
                    tokenBuilder.setLength(0);
                    handleNumberToken(numberToken);

                    // 添加隐式乘法
                    evaluateHigherPrecedenceOperators(SymbolType.乘);
                    operatorStack.push(new Symbol(SymbolType.乘));
                }

                tokenBuilder.append(ch);
                lastWasRightParen = false;
                lastWasNumber = false;
                continue;
            }

            if(expectNumber && !isDigitOrDecimal(ch) && getOperatorType(ch) != null) {
                numberStack.push(new Number(0));
                isFilledZero = true;
            }

            // 处理完整的token（数字或函数名）
            if (tokenBuilder.length() > 0) {
                String token = tokenBuilder.toString();
                tokenBuilder.setLength(0);

                // 处理数字
                if (Character.isDigit(token.charAt(0))) {
                    handleNumberToken(token);
                    lastWasNumber = true;

                    // 隐式乘法处理：数字后跟着左括号
                    if (ch == '(') {
                        evaluateHigherPrecedenceOperators(SymbolType.乘);
                        operatorStack.push(new Symbol(SymbolType.乘));
                        pushedFlag = true;
                    }
                }
                // 处理函数名
                else if (FUNCTIONS.containsKey(token)) {
                    // 函数后面必须跟着左括号
                    if (ch != '(') {
                        throw new RuntimeException("函数 '" + token + "' 后必须跟着 '('");
                    }

                    // 处理隐式乘法：数字后跟着函数
                    if (lastWasNumber || lastWasRightParen) {
                        evaluateHigherPrecedenceOperators(SymbolType.乘);
                        operatorStack.push(new Symbol(SymbolType.乘));
                        pushedFlag = true;
                    }

                    // 将函数名视为特殊运算符压栈
                    operatorStack.push(new Symbol(SymbolType.函数, token));
                }
                else {
                    throw new RuntimeException("未知标识符: " + token);
                }
            }

            // 处理运算符
            switch(ch) {
                case '(':
                    // 处理隐式乘法：数字或右括号后跟着左括号
                    if ((lastWasNumber || lastWasRightParen) && !pushedFlag) {
                        evaluateHigherPrecedenceOperators(SymbolType.乘);
                        operatorStack.push(new Symbol(SymbolType.乘));
                    }

                    operatorStack.push(new Symbol(SymbolType.左括号));
                    expectNumber = true;
                    lastWasRightParen = false;
                    lastWasNumber = false;
                    break;

                case ')':
                    evaluateUntilLeftParenthesis();
                    lastWasRightParen = true;
                    lastWasNumber = false;
                    expectNumber = false;
                    break;

                case ',':
                    // 逗号用于分隔函数参数
                    evaluateHigherPrecedenceOperators(SymbolType.逗号); // 关键修复：计算逗号前的表达式
                    operatorStack.push(new Symbol(SymbolType.逗号)); // 关键修复：压入逗号
                    expectNumber = true;
                    lastWasRightParen = false;
                    lastWasNumber = false;
                    break;

                default:
                    SymbolType currentOp = getOperatorType(ch);
                    evaluateHigherPrecedenceOperators(currentOp);
                    operatorStack.push(new Symbol(currentOp));
                    expectNumber = true;
                    lastWasRightParen = false;
                    lastWasNumber = false;
            }
        }

        // 处理最后一个token
        if (tokenBuilder.length() > 0) {
            String token = tokenBuilder.toString();
            if (Character.isDigit(token.charAt(0))) {
                handleNumberToken(token);
            } else if (FUNCTIONS.containsKey(token)) {
                throw new RuntimeException("函数 '" + token + "' 缺少参数");
            } else {
                throw new RuntimeException("未知标识符: " + token);
            }
        }

        // 计算剩余操作
        evaluateRemainingOperations();

//        if (numberStack.size() != 1) {
//            throw new RuntimeException("表达式不完整");
//        }

        return numberStack.pop(true).value().doubleValue();
    }

    private void handleNumberToken(String token) {
        try {
            numberStack.push(new Number(Double.parseDouble(token)));
        } catch (NumberFormatException e) {
            throw new RuntimeException("无效数字格式: " + token);
        }
    }

    private void evaluateUntilLeftParenthesis() {
        // 收集参数列表
        List<Double> args = new ArrayList<>();
        List<Symbol> tempOperator = new ArrayList<>();
        boolean isPopedLeftParenthesis = false;
        // 先计算括号内的表达式
        while (!operatorStack.isEmpty()) {
            Symbol op = operatorStack.pop(false);
            if (op == null || op.type == SymbolType.逗号) break;

            if (op.type == SymbolType.左括号) {
                operatorStack.pop(true); // 弹出左括号
                isPopedLeftParenthesis = true;
                break;
            }

            evaluateOperation(operatorStack.pop(true).type);
        }

        // 收集参数（从右向左）

        while (!operatorStack.isEmpty() && operatorStack.pop(false).type == SymbolType.逗号) {
            tempOperator.add(operatorStack.pop(true)); // 弹出逗号
            if (!numberStack.isEmpty()) {
                args.add(numberStack.pop(true).value().doubleValue());
            }
        }

        // 添加最后一个参数
        if (!numberStack.isEmpty()) {
            args.add(numberStack.pop(true).value().doubleValue());
        }

        // 反转参数顺序（从左向右）
        double[] argArray = new double[args.size()];
        for (int i = 0; i < args.size(); i++) {
            argArray[i] = args.get(args.size() - 1 - i);
        }

        if (!isPopedLeftParenthesis && operatorStack.pop(false).type == SymbolType.左括号) operatorStack.pop(true);
        // 检查左括号前是否是函数
        if (!operatorStack.isEmpty() && operatorStack.pop(false).type == SymbolType.函数) {
            Symbol funcOp = operatorStack.pop(true);
            double result = applyFunction(funcOp.funcName, argArray);
            numberStack.push(new Number(result));
        } else {
            // 如果不是函数，将参数推回栈中
            for (Symbol Symbol : tempOperator) {
                operatorStack.push(Symbol);
            }
            for (double arg : argArray) {
                numberStack.push(new Number(arg));
            }
        }
    }

    private void evaluateHigherPrecedenceOperators(SymbolType newOp) {
        while (!operatorStack.isEmpty()) {
            Symbol topOp = operatorStack.pop(false);
            if (topOp == null) break;

            // 遇到左括号或栈底标记停止
            if (topOp.type == SymbolType.左括号 || topOp.type == SymbolType.$) {
                break;
            }

            // 函数有最高优先级
            if (topOp.type == SymbolType.函数) {
                // 函数需要立即处理
                evaluateOperation(topOp.type);
                continue;
            }

            // 逗号有最低优先级
            if (topOp.type == SymbolType.逗号) {
                // 如果新运算符优先级高于逗号，则继续处理
                if (hasHigherPrecedence(newOp, topOp.type)) {
                    break;//evaluateOperation(operatorStack.pop(true).type);
                } else {
                    break;
                }
                // continue;
            }

            if(isFilledZero) {
                isFilledZero = false;
                break;
            }
            // 比较优先级
            if (!hasHigherPrecedence(topOp.type, newOp)) {
                break;
            }

            evaluateOperation(operatorStack.pop(true).type);
        }

        if(isFilledZero) {
            isFilledZero = false;
        }
    }

    private void evaluateRemainingOperations() {
        while (numberStack.size() > 1 ||
                (!operatorStack.isEmpty() && operatorStack.pop(false).type == SymbolType.函数)) {
            Symbol op = operatorStack.pop(true);
            if (op == null || op.type == SymbolType.$) {
                break;
            }
            evaluateOperation(op.type);
        }
    }

    private void evaluateOperation(SymbolType opType) {
        switch(opType) {
            case 函数:
                // 函数已在括号处理时处理，这里不应该出现
                throw new RuntimeException("函数调用位置错误");

            case 逗号:
                // 逗号在参数处理中处理，不应单独计算
                break;

            case 加:
            case 减:
            case 乘:
            case 除:
            case 次方:
                if (numberStack.isEmpty()) {
                    throw new RuntimeException("操作数不足");
                }

                ToolList.getInstance().log.info("Do calc: " + opType);
                ToolList.getInstance().log.info("Current NumberStack: " + numberStack.stacks);
                ToolList.getInstance().log.info("Current OperatorStack: " + operatorStack.stacks);

                double right = numberStack.pop(true).value().doubleValue();
                Number temp = numberStack.pop(true);
                double left = temp == null ? 0 : temp.value().doubleValue();

                double result = applyOperator(opType, left, right);
                numberStack.push(new Number(result));
                break;

            default:
                throw new RuntimeException("未知运算符: " + opType);
        }
    }

    private double applyFunction(String funcName, double[] args) {
        FunctionHandler handler = FUNCTIONS.get(funcName);
        if (handler == null) {
            throw new RuntimeException("未知函数: " + funcName);
        }

        try {
            ToolList.getInstance().log.info("Do Func: " + funcName + " -> " + Arrays.toString(args));
            ToolList.getInstance().log.info("Current NumberStack: " + numberStack.stacks);
            ToolList.getInstance().log.info("Current OperatorStack: " + operatorStack.stacks);
            return handler.apply(args);
        } catch (RuntimeException e) {
            throw e; // 重新抛出已知异常
        } catch (Exception e) {
            throw new RuntimeException("函数计算错误: " + funcName);
        }
    }

    private double applyOperator(SymbolType op, double left, double right) {
        switch(op) {
            case 加:
                return left + right;
            case 减:
                return left - right;
            case 乘:
                return left * right;
            case 除:
                return left / right;
            case 次方:
                return Math.pow(left, right);
            default:
                throw new RuntimeException("未知运算符: " + op);
        }
    }

    // 辅助方法
    private boolean isDigitOrDecimal(char ch) {
        return (ch >= '0' && ch <= '9') || ch == '.';
    }

    private boolean hasHigherPrecedence(SymbolType op1, SymbolType op2) {

        ToolList.getInstance().log.info("Compare: " + op1 + " & " + op2);
        ToolList.getInstance().log.info("Current NumberStack: " + numberStack.stacks);
        ToolList.getInstance().log.info("Current OperatorStack: " + operatorStack.stacks);
        ToolList.getInstance().log.info("Result: " + (op1.getPriority() >= op2.getPriority()));
        return op1.getPriority() >= op2.getPriority();

//        // 函数优先级最高
//        if (op1 == Symbol类型.函数) return true;
//        if (op1 == Symbol类型.次方) return true;
//
//        // 逗号优先级最低
//        if (op2 == Symbol类型.逗号) return true;
//
//        // 乘除优先级高于加减
//        return ((op1 == Symbol类型.乘 || op1 == Symbol类型.除) && (op2 == Symbol类型.加 || op2 == Symbol类型.减)) ||
//                ((op1 == Symbol类型.加 || op1 == Symbol类型.减) && (op2 == Symbol类型.加 || op2 == Symbol类型.减)) ||
//                ((op1 == Symbol类型.乘 || op1 == Symbol类型.除) && (op2 == Symbol类型.乘 || op2 == Symbol类型.除));
    }

    private SymbolType getOperatorType(char ch) {
        switch(ch) {
            case '+': return SymbolType.加;
            case '-': return SymbolType.减;
            case '*': return SymbolType.乘;
            case '/': return SymbolType.除;
            case '^': return SymbolType.次方;
            default: return null;
        }
    }
}