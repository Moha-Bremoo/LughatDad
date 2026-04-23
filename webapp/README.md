# لغة ضاد — LughatDad

> محرر ومفسر تفاعلي عبر الإنترنت لـ**لغة برمجة عربية** كاملة  
> An online editor + interpreter for a custom Arabic general-purpose programming language.

---

## 🚀 Quick Start

Open `index.html` directly in any modern browser — **no server needed**.

```bash
open index.html    # macOS
```

## ☁️ Deploy to Netlify (30 seconds)

1. Go to [app.netlify.com/drop](https://app.netlify.com/drop)
2. Drag the `webapp/` folder onto the page
3. Done! You get a live URL instantly.

## ☁️ Deploy to Vercel

```bash
npx vercel --prod
```

(Run from inside the `webapp/` directory.)

---

## ✨ Features

- 🖊️ **CodeMirror 6 editor** with RTL support + Arabic monospace font
- 🎨 **Syntax highlighting**: keywords in purple, strings in green, numbers in amber, comments in gray
- ▶️ **Run button** + `Ctrl+Enter` keyboard shortcut
- 📦 **6 built-in examples**: hello world, loop, grade check, nested if-in-while, multiplication table, booleans
- ❌ **Arabic runtime errors**: `خطأ: متغير غير معرّف`, `خطأ: القسمة على صفر`
- 🔁 **100% client-side** — no backend, no Node.js required

---

## 📝 Language Reference

| Keyword | Meaning              |
| ------- | -------------------- |
| `متغير` | variable declaration |
| `اطبع`  | print                |
| `إذا`   | if                   |
| `وإلا`  | else                 |
| `بينما` | while                |
| `صح`    | true                 |
| `خطأ`   | false                |

### Syntax Example

```arabic
متغير عداد = 1؛
بينما (عداد <= 3) {
    إذا (عداد == 2) {
        اطبع("هذا هو العدد اثنين")؛
    } وإلا {
        اطبع(عداد)؛
    }
    عداد = عداد + 1؛
}
```

Output:

```
1
هذا هو العدد اثنين
3
```

---

## 🏗️ Architecture

Single-file `index.html` — pure vanilla JS, no build step:

| Layer       | Technology                              |
| ----------- | --------------------------------------- |
| Editor      | CodeMirror 6 (ESM via esm.sh CDN)       |
| Interpreter | Custom JS: Lexer → Parser → Evaluator   |
| Styling     | Vanilla CSS                             |
| Fonts       | Google Fonts: Cairo + Noto Naskh Arabic |

The interpreter mirrors the original `LughatDad.jj` JavaCC grammar exactly:

- `lexer`: tokenizer (handles keywords before identifiers, Eastern Arabic digits)
- `parser`: recursive-descent parser following the BNF grammar
- `evaluator`: tree-walking interpreter with a `Map`-based symbol table

---

_Based on the LughatDad compiler project 2026_
