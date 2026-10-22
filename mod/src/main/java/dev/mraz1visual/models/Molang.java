package dev.mraz1visual.models;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Маленький Molang для значений ключевых кадров: числа, + − * /, скобки, унарный минус,
 * math.sin/cos (градусы), abs, clamp, lerp, min, max, sqrt, pow, floor, ceil, round, mod, pi,
 * query.anim_time / query.life_time. Всё прочее (variable.*, сложные query) — 0 с предупреждением в лог.
 * Разбор — один раз при загрузке, в кадре только eval.
 */
public final class Molang {
    private Molang() {}

    public interface Expr {
        float eval(float t);
    }

    public record Const(float v) implements Expr {
        @Override public float eval(float t) { return v; }
    }

    /** Строка или число → выражение. warn получает текст предупреждения (по одному на выражение). */
    public static Expr parse(String src, Consumer<String> warn) {
        String s = src.trim();
        if (s.isEmpty()) return new Const(0);
        try {
            return new Const(Float.parseFloat(s));
        } catch (NumberFormatException ignored) {
        }
        try {
            Parser p = new Parser(s.toLowerCase(Locale.ROOT), warn);
            Expr e = p.expr();
            p.ws();
            if (p.i < p.s.length()) throw new IllegalArgumentException("лишнее: " + p.s.substring(p.i));
            if (p.unknown) warn.accept("Molang: неизвестное в «" + src + "» → 0");
            return e;
        } catch (RuntimeException ex) {
            warn.accept("Molang: не разобрано «" + src + "» (" + ex.getMessage() + ") → 0");
            return new Const(0);
        }
    }

    private static final class Parser {
        final String s;
        final Consumer<String> warn;
        int i;
        boolean unknown;

        Parser(String s, Consumer<String> warn) {
            this.s = s;
            this.warn = warn;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        boolean eat(char c) {
            ws();
            if (i < s.length() && s.charAt(i) == c) { i++; return true; }
            return false;
        }

        Expr expr() {
            Expr a = term();
            while (true) {
                if (eat('+')) { Expr l = a, r = term(); a = t -> l.eval(t) + r.eval(t); }
                else if (eat('-')) { Expr l = a, r = term(); a = t -> l.eval(t) - r.eval(t); }
                else return a;
            }
        }

        Expr term() {
            Expr a = unary();
            while (true) {
                if (eat('*')) { Expr l = a, r = unary(); a = t -> l.eval(t) * r.eval(t); }
                else if (eat('/')) { Expr l = a, r = unary(); a = t -> { float d = r.eval(t); return d == 0 ? 0 : l.eval(t) / d; }; }
                else return a;
            }
        }

        Expr unary() {
            if (eat('-')) { Expr a = unary(); return t -> -a.eval(t); }
            if (eat('+')) return unary();
            return atom();
        }

        Expr atom() {
            ws();
            if (eat('(')) {
                Expr e = expr();
                if (!eat(')')) throw new IllegalArgumentException("нет )");
                return e;
            }
            int st = i;
            if (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                if (i < s.length() && s.charAt(i) == 'f') i++;
                return new Const(Float.parseFloat(s.substring(st, i).replace("f", "")));
            }
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
            String id = s.substring(st, i);
            if (id.isEmpty()) throw new IllegalArgumentException("ожидалось значение");
            if (eat('(')) {
                java.util.List<Expr> args = new java.util.ArrayList<>();
                if (!eat(')')) {
                    do args.add(expr()); while (eat(','));
                    if (!eat(')')) throw new IllegalArgumentException("нет )");
                }
                return call(id, args);
            }
            return switch (id) {
                case "query.anim_time", "q.anim_time", "query.life_time", "q.life_time" -> t -> t;
                case "math.pi" -> new Const((float) Math.PI);
                default -> {
                    unknown = true;
                    yield new Const(0);
                }
            };
        }

        Expr call(String fn, java.util.List<Expr> a) {
            Expr x = a.isEmpty() ? new Const(0) : a.get(0);
            Expr y = a.size() > 1 ? a.get(1) : new Const(0);
            Expr z = a.size() > 2 ? a.get(2) : new Const(0);
            String f = fn.startsWith("math.") ? fn.substring(5) : fn;
            return switch (f) {
                case "sin" -> t -> (float) Math.sin(Math.toRadians(x.eval(t)));
                case "cos" -> t -> (float) Math.cos(Math.toRadians(x.eval(t)));
                case "abs" -> t -> Math.abs(x.eval(t));
                case "sqrt" -> t -> (float) Math.sqrt(Math.max(0, x.eval(t)));
                case "floor" -> t -> (float) Math.floor(x.eval(t));
                case "ceil" -> t -> (float) Math.ceil(x.eval(t));
                case "round" -> t -> Math.round(x.eval(t));
                case "min" -> t -> Math.min(x.eval(t), y.eval(t));
                case "max" -> t -> Math.max(x.eval(t), y.eval(t));
                case "pow" -> t -> (float) Math.pow(x.eval(t), y.eval(t));
                case "mod" -> t -> { float d = y.eval(t); return d == 0 ? 0 : x.eval(t) % d; };
                case "clamp" -> t -> Math.max(y.eval(t), Math.min(z.eval(t), x.eval(t)));
                case "lerp" -> t -> { float k = z.eval(t); return x.eval(t) + (y.eval(t) - x.eval(t)) * k; };
                case "random", "random_integer", "die_roll" -> new Const(0);
                default -> {
                    unknown = true;
                    yield new Const(0);
                }
            };
        }
    }
}
