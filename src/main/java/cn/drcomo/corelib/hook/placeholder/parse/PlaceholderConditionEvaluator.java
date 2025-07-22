package cn.drcomo.corelib.hook.placeholder.parse;

import cn.drcomo.corelib.math.FormulaCalculator;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import cn.drcomo.corelib.async.AsyncTaskManager;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ======================================================================
 * PlaceholderConditionEvaluator（无日志 / 无自定义占位符 轻量版）
 * ----------------------------------------------------------------------
 * - 功能：解析并求值包含括号、逻辑运算符 (&&, ||) 及比较运算符的表达式。
 * - 支持占位符：仅通过 PlaceholderAPIUtil 解析 %xxx%。
 * - 支持数学函数：min/max/floor/ceil/round/abs/pow/sqrt……（通过 FormulaCalculator）。
 * - 线程模型：同步 parse(...)；异步 parseAndEvaluateAsync(...) 使用 Bukkit 任务。
 * - 调试支持：通过 DebugUtil 提供调试日志。
 * ======================================================================
 */
public class PlaceholderConditionEvaluator {

    // 插件实例，用于调度任务
    private final JavaPlugin plugin;
    // 调试工具实例
    private final DebugUtil debugger;
    // PlaceholderAPI 工具
    private final PlaceholderAPIUtil placeholderUtil;
    // 可选的异步任务执行器
    private final Executor asyncExecutor;
    // 可选的 AsyncTaskManager
    private final AsyncTaskManager taskManager;

    /**
     * 创建条件解析器实例（默认异步执行器）。
     *
     * @param pluginInstance  Bukkit 插件实例
     * @param debugger        已创建的 DebugUtil
     * @param placeholderUtil PlaceholderAPIUtil 实例
     */
    public PlaceholderConditionEvaluator(JavaPlugin pluginInstance,
                                         DebugUtil debugger,
                                         PlaceholderAPIUtil placeholderUtil) {
        this(pluginInstance, debugger, placeholderUtil, (Executor) null);
    }

    /**
     * 创建条件解析器实例，使用自定义 {@link Executor} 执行异步任务。
     *
     * @param pluginInstance  Bukkit 插件实例
     * @param debugger        已创建的 DebugUtil
     * @param placeholderUtil PlaceholderAPIUtil 实例
     * @param executor        自定义异步执行器，可为 {@code null}
     */
    public PlaceholderConditionEvaluator(JavaPlugin pluginInstance,
                                         DebugUtil debugger,
                                         PlaceholderAPIUtil placeholderUtil,
                                         Executor executor) {
        this.plugin = pluginInstance;
        this.debugger = debugger;
        this.placeholderUtil = placeholderUtil;
        this.asyncExecutor = executor;
        this.taskManager = null;
        this.debugger.debug("PlaceholderConditionEvaluator 已初始化");
    }

    /**
     * 创建条件解析器实例，使用 {@link AsyncTaskManager} 执行异步任务。
     *
     * @param pluginInstance  Bukkit 插件实例
     * @param debugger        已创建的 DebugUtil
     * @param placeholderUtil PlaceholderAPIUtil 实例
     * @param manager         已创建的 AsyncTaskManager
     */
    public PlaceholderConditionEvaluator(JavaPlugin pluginInstance,
                                         DebugUtil debugger,
                                         PlaceholderAPIUtil placeholderUtil,
                                         AsyncTaskManager manager) {
        this.plugin = pluginInstance;
        this.debugger = debugger;
        this.placeholderUtil = placeholderUtil;
        this.asyncExecutor = null;
        this.taskManager = manager;
        this.debugger.debug("PlaceholderConditionEvaluator 已初始化");
    }

    /* ==================================================================
     * 1. 公共工具
     * ================================================================== */

    /** 判断字符串是否为布尔值 */
    private static boolean isBooleanResult(String str) {
        if (str == null) return false;
        String trimmed = str.trim().toLowerCase();
        return "true".equals(trimmed) || "false".equals(trimmed);
    }

    /* ==================================================================
     * 2. 数学表达式计算
     * ================================================================== */

    /**
     * 计算数学表达式（使用统一的 FormulaCalculator）
     * @param expression 数学表达式字符串
     * @return 计算结果，若解析失败则返回 0.0
     */
    public double calculateMathExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return FormulaCalculator.calculate(expression);
        } catch (Exception e) {
            debugger.warn("数学表达式计算失败: " + expression);
            return 0.0; // 解析失败返回 0
        }
    }

    /**
     * 判断字符串中是否包含数学符号或函数
     * @param expression 输入表达式
     * @return 是否包含数学表达式
     */
    public boolean containsMathExpression(String expression) {
        return expression != null && !expression.isEmpty()
                && FormulaCalculator.containsMathExpression(expression);
    }

    /* ==================================================================
     * 3. 布尔表达式校验（同步 / 异步）
     * ================================================================== */

    /**
     * 同步：多行 AND 校验
     * @param player 玩家上下文
     * @param lines 条件行列表
     * @return 所有条件是否都为真
     */
    public boolean checkAllLines(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        for (String line : lines) {
            try {
                if (!parse(player, line)) {
                    debugger.debug("条件校验失败: " + line);
                    return false;
                }
            } catch (ParseException e) {
                debugger.warn("解析条件时出错: " + line + "，原因: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * 同步：单行解析
     * @param player 玩家上下文
     * @param expression 条件表达式
     * @return 解析结果
     * @throws ParseException 解析失败时抛出
     */
    public boolean parse(Player player, String expression) throws ParseException {
        Tokenizer tokenizer = new Tokenizer(expression);
        Parser parser = new Parser(tokenizer, placeholderUtil);
        Node ast = parser.parseExpression();
        if (tokenizer.getCurrentToken().getType() != TokenType.EOF) {
            throw new ParseException("多余内容: " + tokenizer.getCurrentToken().getValue());
        }
        boolean result = ast.evaluate(player);
        debugger.debug("解析表达式 " + expression + " 结果: " + result);
        return result;
    }

    /**
     * 异步：单行解析（高并发友好）
     * @param expression 条件表达式
     * @param player 玩家上下文
     * @return CompletableFuture 表示异步解析结果
     */
    public CompletableFuture<Boolean> parseAndEvaluateAsync(String expression, Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (plugin == null) {
            debugger.warn("插件未初始化，异步解析失败");
            future.complete(false);
            return future;
        }
        runAsyncTask(() -> {
            boolean result;
            try {
                result = parse(player, expression);
            } catch (Exception e) {
                debugger.warn("异步解析失败: " + expression + "，原因: " + e.getMessage());
                result = false;
            }
            boolean finalResult = result;
            runSyncTask(() -> future.complete(finalResult));
        });
        return future;
    }

    /**
     * 异步：多行 AND 校验
     * @param player 玩家上下文
     * @param lines 条件行列表
     * @return CompletableFuture 表示异步校验结果
     */
    public CompletableFuture<Boolean> checkAllLinesAsync(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        return checkLinesRecursivelyAsync(player, lines, 0);
    }

    /**
     * 递归串行异步多行检查
     * @param player 玩家上下文
     * @param lines 条件行列表
     * @param index 当前检查索引
     * @return CompletableFuture 表示递归校验结果
     */
    private CompletableFuture<Boolean> checkLinesRecursivelyAsync(Player player, List<String> lines, int index) {
        if (index >= lines.size()) {
            return CompletableFuture.completedFuture(true);
        }
        return parseAndEvaluateAsync(lines.get(index), player).thenCompose(pass -> {
            if (!pass) {
                debugger.debug("异步多行校验失败于: " + lines.get(index));
                return CompletableFuture.completedFuture(false);
            }
            return checkLinesRecursivelyAsync(player, lines, index + 1);
        });
    }

    /* ★—————————————————— 调度工具 ——————————————————★ */

    /**
     * 在主线程执行任务
     * @param task 要执行的任务
     */
    private void runSyncTask(Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTask(plugin);
    }

    /**
     * 在异步线程执行任务
     * @param task 要执行的任务
     */
    private void runAsyncTask(Runnable task) {
        if (taskManager != null) {
            taskManager.submitAsync(task);
            return;
        }
        if (asyncExecutor != null) {
            asyncExecutor.execute(task);
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskAsynchronously(plugin);
    }

    /* ==================================================================
     * 4. 词法 / 语法 / AST 实现
     * ================================================================== */

    /* ---------- Token 定义 ---------- */
    private enum TokenType { LPAREN, RPAREN, AND, OR, OPERATOR, LITERAL, EOF }

    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType t, String v) {
            this.type = t;
            this.value = v;
        }

        TokenType getType() {
            return type;
        }

        String getValue() {
            return value;
        }
    }

    /* ---------- AST 节点接口 ---------- */
    private interface Node {
        boolean evaluate(Player player);
    }

    private static class OrNode implements Node {
        private final Node left, right;

        OrNode(Node l, Node r) {
            this.left = l;
            this.right = r;
        }

        public boolean evaluate(Player p) {
            return left.evaluate(p) || right.evaluate(p);
        }
    }

    private static class AndNode implements Node {
        private final Node left, right;

        AndNode(Node l, Node r) {
            this.left = l;
            this.right = r;
        }

        public boolean evaluate(Player p) {
            return left.evaluate(p) && right.evaluate(p);
        }
    }

    /* ---------- 比较类型 & 实现 ---------- */
    private enum Cmp {
        GT(">"), GE(">="), LT("<"), LE("<="), EQ("=="), NE("!="),
        STR_CONTAINS(">>"), STR_NOT_CONTAINS("!>>"),
        STR_CONTAINED("<<"), STR_NOT_CONTAINED("!<<");

        final String sym;

        Cmp(String s) {
            this.sym = s;
        }

        public String toString() {
            return sym;
        }

        static Cmp of(String s) {
            for (Cmp c : values()) {
                if (c.sym.equals(s)) return c;
            }
            throw new IllegalArgumentException("未知运算符: " + s);
        }
    }

    private static class ComparisonNode implements Node {
        private final String leftRaw, rightRaw;
        private final Cmp cmp;
        private final PlaceholderAPIUtil util;

        ComparisonNode(String l, String op, String r, PlaceholderAPIUtil util) {
            this.leftRaw = l;
            this.rightRaw = r;
            this.cmp = Cmp.of(op);
            this.util = util;
        }

        public boolean evaluate(Player player) {
            String left = util.parse(player, leftRaw);
            String right = util.parse(player, rightRaw);
            return evaluateComparison(left, right, cmp);
        }

        /**
         * 核心比较逻辑
         * @param left 左侧值
         * @param right 右侧值
         * @param op 比较运算符
         * @return 比较结果
         */
        private boolean evaluateComparison(String left, String right, Cmp op) {
            if (isNumericOp(op)) {
                Double a = parseNumber(left);
                Double b = parseNumber(right);
                if (a != null && b != null) {
                    return numericCompare(a, b, op);
                }
                return false; // 数值运算符但非数字，返回 false
            }
            if (isBooleanResult(left) || isBooleanResult(right)) {
                return booleanCompare(Boolean.parseBoolean(left), Boolean.parseBoolean(right), op);
            }
            return stringCompare(left, right, op);
        }

        private static boolean isNumericOp(Cmp op) {
            return op == Cmp.GT || op == Cmp.GE || op == Cmp.LT || op == Cmp.LE;
        }

        private static Double parseNumber(String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static boolean numericCompare(double a, double b, Cmp op) {
            switch (op) {
                case GT: return a > b;
                case GE: return a >= b;
                case LT: return a < b;
                case LE: return a <= b;
                case EQ: return a == b;
                case NE: return a != b;
                default: return false;
            }
        }

        private static boolean booleanCompare(boolean a, boolean b, Cmp op) {
            return (op == Cmp.EQ) ? a == b : a != b;
        }

        private static boolean stringCompare(String left, String right, Cmp op) {
            switch (op) {
                case EQ: return left.equals(right);
                case NE: return !left.equals(right);
                case STR_CONTAINS: return left.contains(right);
                case STR_NOT_CONTAINS: return !left.contains(right);
                case STR_CONTAINED: return right.contains(left);
                case STR_NOT_CONTAINED: return !right.contains(left);
                case GT: return left.compareTo(right) > 0;
                case GE: return left.compareTo(right) >= 0;
                case LT: return left.compareTo(right) < 0;
                case LE: return left.compareTo(right) <= 0;
                default: return false;
            }
        }
    }

    /* ---------- 语法分析器 ---------- */
    private class Parser {
        private final Tokenizer tokenizer;
        private final PlaceholderAPIUtil util;

        Parser(Tokenizer t, PlaceholderAPIUtil util) {
            this.tokenizer = t;
            this.util = util;
        }

        Node parseExpression() throws ParseException {
            return parseOr();
        }

        private Node parseOr() throws ParseException {
            Node node = parseAnd();
            while (tokenizer.getCurrentToken().getType() == TokenType.OR) {
                tokenizer.nextToken();
                node = new OrNode(node, parseAnd());
            }
            return node;
        }

        private Node parseAnd() throws ParseException {
            Node node = parsePrimary();
            while (tokenizer.getCurrentToken().getType() == TokenType.AND) {
                tokenizer.nextToken();
                node = new AndNode(node, parsePrimary());
            }
            return node;
        }

        private Node parsePrimary() throws ParseException {
            if (tokenizer.getCurrentToken().getType() == TokenType.LPAREN) {
                tokenizer.nextToken();
                Node node = parseExpression();
                if (tokenizer.getCurrentToken().getType() != TokenType.RPAREN) {
                    throw new ParseException("缺少右括号 )");
                }
                tokenizer.nextToken();
                return node;
            }
            return parseComparison();
        }

        private Node parseComparison() throws ParseException {
            String left = expect(TokenType.LITERAL, "缺少左操作数");
            String op = expect(TokenType.OPERATOR, "缺少比较运算符");
            String right = expect(TokenType.LITERAL, "缺少右操作数");
            return new ComparisonNode(left, op, right, util);
        }

        private String expect(TokenType type, String msg) throws ParseException {
            Token cur = tokenizer.getCurrentToken();
            if (cur.getType() != type) {
                throw new ParseException(msg + ": " + cur.getValue());
            }
            tokenizer.nextToken();
            return cur.getValue();
        }
    }

    /* ---------- 词法分析器 ---------- */
    private static class Tokenizer {
        private final String input;
        private int pos = 0;
        private Token cur;

        // 支持的比较运算符
        private static final String[] OPS = {
                "!>>", "!<<", ">=", "<=", "==", "!=",
                ">>", "<<", ">", "<", "="
        };

        Tokenizer(String in) throws ParseException {
            if (in == null) throw new ParseException("表达式为空");
            this.input = in;
            nextToken();
        }

        Token getCurrentToken() {
            return cur;
        }

        void nextToken() throws ParseException {
            skipWhitespace();
            if (pos >= input.length()) {
                cur = new Token(TokenType.EOF, "");
                return;
            }
            char ch = input.charAt(pos);

            // 括号
            if (ch == '(') {
                pos++;
                cur = new Token(TokenType.LPAREN, "(");
                return;
            }
            if (ch == ')') {
                pos++;
                cur = new Token(TokenType.RPAREN, ")");
                return;
            }
            // 逻辑运算符
            if (match("&&")) {
                pos += 2;
                cur = new Token(TokenType.AND, "&&");
                return;
            }
            if (match("||")) {
                pos += 2;
                cur = new Token(TokenType.OR, "||");
                return;
            }
            // 比较运算符
            for (String op : OPS) {
                if (match(op)) {
                    pos += op.length();
                    cur = new Token(TokenType.OPERATOR, op);
                    return;
                }
            }
            // 字面量
            cur = new Token(TokenType.LITERAL, readLiteral());
        }

        private boolean match(String s) {
            return input.regionMatches(pos, s, 0, s.length());
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean startsWithOp() {
            for (String op : OPS) {
                if (match(op)) return true;
            }
            return false;
        }

        private String readLiteral() {
            int start = pos;
            boolean inQuote = false;
            char quote = 0;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (inQuote) {
                    if (c == '\\') {
                        pos += 2;
                        continue;
                    }
                    if (c == quote) {
                        pos++;
                        break;
                    }
                    pos++;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quote = c;
                    pos++;
                    continue;
                }
                if (Character.isWhitespace(c) || c == '(' || c == ')' ||
                        match("&&") || match("||") || startsWithOp()) {
                    break;
                }
                pos++;
            }
            return input.substring(start, pos);
        }
    }
}