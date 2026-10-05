#!/usr/bin/env python3
"""Agent local: model GGUF prin llama-server + unelte (shell, fisiere). Doar biblioteca standard."""
import json, os, subprocess, sys, time, urllib.request

HOME = os.path.expanduser("~")
DIR = os.path.join(HOME, ".agent")
CFG = os.path.join(DIR, "config.json")
MODELS = os.path.join(DIR, "models")

# Valori de pornire. Toate se schimba cu /set sau direct in ~/.agent/config.json.
DEFAULTS = {
    "model": "",             # calea catre fisierul .gguf
    "port": 8080,
    "ctx": 8192,             # context; 0 = lasa llama-server sa decida
    "threads": 0,            # 0 = implicit llama-server
    "server_extra": [],      # argumente suplimentare pentru llama-server
    "auto": False,           # True = executa fara sa intrebe
    "max_tool_output": 0,    # 0 = fara taiere; altfel nr. maxim de caractere intoarse modelului
    "system": ("Esti un agent care ruleaza pe un telefon Android (Termux). Executa ce iti cere utilizatorul. "
               "Ai unelte: shell (bash; ai curl, python, git; poti cauta pe web cu curl), read_file, write_file. "
               "Foloseste uneltele cand ajuta si spune scurt ce ai facut."),
}

TOOLS = [
    {"type": "function", "function": {
        "name": "shell", "description": "Ruleaza o comanda bash pe telefon.",
        "parameters": {"type": "object", "properties": {"command": {"type": "string"}}, "required": ["command"]}}},
    {"type": "function", "function": {
        "name": "read_file", "description": "Citeste un fisier text.",
        "parameters": {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}}},
    {"type": "function", "function": {
        "name": "write_file", "description": "Scrie (suprascrie) un fisier text.",
        "parameters": {"type": "object", "properties": {"path": {"type": "string"}, "content": {"type": "string"}},
                       "required": ["path", "content"]}}},
]

server_proc = None


def load():
    os.makedirs(MODELS, exist_ok=True)
    cfg = dict(DEFAULTS)
    try:
        with open(CFG) as f:
            cfg.update(json.load(f))
    except Exception:
        pass
    return cfg


def save(cfg):
    os.makedirs(DIR, exist_ok=True)
    with open(CFG, "w") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)


def server_up(port):
    try:
        urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=2).read()
        return True
    except Exception:
        return False


def ensure_server(cfg):
    global server_proc
    if server_up(cfg["port"]):
        return True
    if not cfg["model"] or not os.path.exists(cfg["model"]):
        print("Nu ai un model. Foloseste: /model <url-sau-cale-.gguf>")
        return False
    cmd = ["llama-server", "-m", cfg["model"], "--jinja", "--host", "127.0.0.1", "--port", str(cfg["port"])]
    if cfg["ctx"]:
        cmd += ["-c", str(cfg["ctx"])]
    if cfg["threads"]:
        cmd += ["-t", str(cfg["threads"])]
    cmd += list(cfg["server_extra"])
    log = open(os.path.join(DIR, "server.log"), "ab")
    try:
        server_proc = subprocess.Popen(cmd, stdout=log, stderr=log, stdin=subprocess.DEVNULL)
    except FileNotFoundError:
        print("llama-server nu e instalat (apt install llama-cpp).")
        return False
    print("Incarc modelul... (Ctrl-C = renunt)")
    try:
        while not server_up(cfg["port"]):
            if server_proc.poll() is not None:
                print("Serverul s-a oprit. Vezi ~/.agent/server.log")
                return False
            time.sleep(2)
    except KeyboardInterrupt:
        stop_server()
        return False
    return True


def stop_server():
    global server_proc
    if server_proc and server_proc.poll() is None:
        server_proc.terminate()
        try:
            server_proc.wait(10)
        except Exception:
            server_proc.kill()
    server_proc = None


def chat(cfg, messages):
    body = {"messages": messages, "tools": TOOLS, "stream": True}
    req = urllib.request.Request(f"http://127.0.0.1:{cfg['port']}/v1/chat/completions",
                                 data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    text, calls = "", {}
    with urllib.request.urlopen(req) as r:
        for raw in r:
            line = raw.decode("utf-8", "replace").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            choices = json.loads(data).get("choices") or []
            if not choices:
                continue
            delta = choices[0].get("delta") or {}
            if delta.get("content"):
                text += delta["content"]
                sys.stdout.write(delta["content"])
                sys.stdout.flush()
            for tc in delta.get("tool_calls") or []:
                c = calls.setdefault(tc.get("index", 0), {"id": "", "name": "", "args": ""})
                if tc.get("id"):
                    c["id"] = tc["id"]
                fn = tc.get("function") or {}
                if fn.get("name") and not c["name"]:
                    c["name"] = fn["name"]
                if fn.get("arguments"):
                    c["args"] += fn["arguments"]
    return text, [calls[i] for i in sorted(calls)]


def approve(cfg, what):
    if cfg["auto"]:
        print(f"\n[rulez] {what}")
        return True
    ans = input(f"\n[agentul vrea] {what}\n  executa? [d/N/t=toate] ").strip().lower()
    if ans in ("t", "toate", "a", "all"):
        cfg["auto"] = True
        return True
    return ans in ("d", "da", "y", "yes")


def clip(cfg, s):
    lim = cfg["max_tool_output"]
    if lim and len(s) > lim:
        h = lim // 2
        return s[:h] + f"\n...[taiat {len(s) - lim} caractere]...\n" + s[-h:]
    return s


def run_tool(cfg, name, raw_args):
    try:
        a = json.loads(raw_args or "{}")
    except Exception:
        return "Argumente JSON invalide: " + str(raw_args)
    try:
        if name == "shell":
            cmd = a.get("command", "")
            if not approve(cfg, "shell: " + cmd):
                return "Refuzat de utilizator."
            try:
                p = subprocess.run(["bash", "-c", cmd], capture_output=True, text=True, stdin=subprocess.DEVNULL)
                out = (p.stdout or "") + (p.stderr or "") + f"\n[exit {p.returncode}]"
            except KeyboardInterrupt:
                out = "[intrerupt de utilizator]"
        elif name == "read_file":
            with open(os.path.expanduser(a.get("path", "")), errors="replace") as f:
                out = f.read()
        elif name == "write_file":
            path = os.path.expanduser(a.get("path", ""))
            if not approve(cfg, f"scrie fisier: {path}"):
                return "Refuzat de utilizator."
            os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
            with open(path, "w") as f:
                f.write(a.get("content", ""))
            out = "OK"
        else:
            out = "Unealta necunoscuta: " + name
    except Exception as e:
        out = f"Eroare: {e}"
    return clip(cfg, out)


def turn(cfg, messages):
    n = 0
    while True:
        try:
            text, calls = chat(cfg, messages)
        except KeyboardInterrupt:
            print("\n[oprit]")
            return
        except Exception as e:
            print(f"\nEroare la model: {e}")
            return
        msg = {"role": "assistant", "content": text}
        if calls:
            for c in calls:
                n += 1
                c["id"] = c["id"] or f"call_{n}"
            msg["tool_calls"] = [{"id": c["id"], "type": "function",
                                  "function": {"name": c["name"], "arguments": c["args"] or "{}"}} for c in calls]
        messages.append(msg)
        if not calls:
            print()
            return
        for c in calls:
            messages.append({"role": "tool", "tool_call_id": c["id"], "content": run_tool(cfg, c["name"], c["args"])})


HELP = """Comenzi:
  /model <url|cale>   descarca (curl) sau seteaza modelul .gguf
  /models             lista modelelor descarcate
  /auto on|off        executa fara sa intrebe / intreaba inainte
  /set <cheie> <val>  ex: /set ctx 16384   (ctx, threads, port, max_tool_output)
  /system <text>      schimba instructiunea de baza
  /restart            reporneste serverul de model
  /new                conversatie noua
  /show               arata setarile
  /quit               iesire
Ctrl-C opreste generarea sau comanda curenta."""


def command(cfg, line, state):
    parts = line.split(maxsplit=2)
    c = parts[0]
    if c == "/help":
        print(HELP)
    elif c == "/quit":
        return False
    elif c == "/new":
        state["messages"] = [{"role": "system", "content": cfg["system"]}]
        print("Conversatie noua.")
    elif c == "/show":
        print(json.dumps(cfg, indent=2, ensure_ascii=False))
    elif c == "/auto" and len(parts) > 1:
        cfg["auto"] = parts[1] == "on"
        save(cfg)
        print("auto:", cfg["auto"])
    elif c == "/system" and len(parts) > 1:
        cfg["system"] = line.split(maxsplit=1)[1]
        save(cfg)
        state["messages"][0] = {"role": "system", "content": cfg["system"]}
    elif c == "/set" and len(parts) > 2:
        k, v = parts[1], parts[2]
        if k not in cfg:
            print("Cheie necunoscuta:", k)
        else:
            cfg[k] = int(v) if isinstance(DEFAULTS[k], int) and not isinstance(DEFAULTS[k], bool) else v
            save(cfg)
            print(f"{k} = {cfg[k]}  (/restart ca sa se aplice la server)")
    elif c == "/restart":
        stop_server()
        print("Serverul va reporni la urmatorul mesaj.")
    elif c == "/models":
        for f in sorted(os.listdir(MODELS)):
            print(os.path.join(MODELS, f))
    elif c == "/model" and len(parts) > 1:
        src = parts[1]
        if src.startswith("http"):
            dest = os.path.join(MODELS, src.split("?")[0].rstrip("/").split("/")[-1])
            print("Descarc in", dest, "(se poate relua cu aceeasi comanda)")
            subprocess.run(["curl", "-L", "-C", "-", "--fail", "-o", dest, src])
            src = dest
        cfg["model"] = os.path.expanduser(src)
        save(cfg)
        stop_server()
        print("Model:", cfg["model"])
    else:
        print("Comanda necunoscuta. /help")
    return True


def main():
    cfg = load()
    state = {"messages": [{"role": "system", "content": cfg["system"]}]}
    print("Agent local. /help pentru comenzi. Mod:", "automat" if cfg["auto"] else "cu confirmare")
    if not cfg["model"]:
        print("Incepe cu: /model <url-catre-un-fisier-.gguf>")
    try:
        while True:
            try:
                line = input("\n> ").strip()
            except KeyboardInterrupt:
                print()
                continue
            except EOFError:
                break
            if not line:
                continue
            if line.startswith("/"):
                if not command(cfg, line, state):
                    break
                continue
            if not ensure_server(cfg):
                continue
            state["messages"].append({"role": "user", "content": line})
            turn(cfg, state["messages"])
    finally:
        stop_server()


if __name__ == "__main__":
    main()
