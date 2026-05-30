const state = {
    sessionId: localStorage.getItem("myagent.sessionId") || crypto.randomUUID(),
    busy: false
};

const messages = document.getElementById("messages");
const form = document.getElementById("chatForm");
const question = document.getElementById("question");
const projectPath = document.getElementById("projectPath");
const chooseProjectButton = document.getElementById("chooseProjectButton");
const sessionId = document.getElementById("sessionId");
const statusText = document.getElementById("statusText");
const sendButton = document.getElementById("sendButton");
const clearButton = document.getElementById("clearButton");
const usedFiles = document.getElementById("usedFiles");
const pathDialog = document.getElementById("pathDialog");
const closeDialogButton = document.getElementById("closeDialogButton");
const browserRoot = document.getElementById("browserRoot");
const currentFolder = document.getElementById("currentFolder");
const parentButton = document.getElementById("parentButton");
const selectCurrentButton = document.getElementById("selectCurrentButton");
const directoryList = document.getElementById("directoryList");

localStorage.setItem("myagent.sessionId", state.sessionId);
sessionId.textContent = state.sessionId;

appendMessage("assistant", "你好，我已经准备好了。");
initializeWorkspace();

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = question.value.trim();
    if (!text || state.busy) {
        return;
    }

    appendMessage("user", text);
    question.value = "";
    setBusy(true);
    const pending = appendMessage("assistant thinking", "Thinking...");

    try {
        const response = await fetch("/api/agent/chat", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                sessionId: state.sessionId,
                projectPath: state.projectPath,
                question: text
            })
        });

        const payload = await response.json();
        pending.remove();

        if (!response.ok) {
            appendMessage("error", payload.message || "Request failed");
            return;
        }

        state.sessionId = payload.sessionId || state.sessionId;
        localStorage.setItem("myagent.sessionId", state.sessionId);
        sessionId.textContent = state.sessionId;
        appendMessage("assistant", payload.answer || "");
        renderUsedFiles(payload.usedFiles || []);
    } catch (error) {
        pending.remove();
        appendMessage("error", error.message || "Network error");
    } finally {
        setBusy(false);
    }
});

clearButton.addEventListener("click", () => {
    state.sessionId = crypto.randomUUID();
    localStorage.setItem("myagent.sessionId", state.sessionId);
    sessionId.textContent = state.sessionId;
    messages.innerHTML = "";
    renderUsedFiles([]);
    appendMessage("assistant", "新的会话已经开始。");
});

question.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
    }
});

chooseProjectButton.addEventListener("click", () => {
    openPathDialog(state.projectPath || state.workspaceRoot);
});

closeDialogButton.addEventListener("click", closePathDialog);

pathDialog.addEventListener("click", (event) => {
    if (event.target === pathDialog) {
        closePathDialog();
    }
});

parentButton.addEventListener("click", () => {
    if (state.browserParentPath) {
        loadDirectories(state.browserParentPath);
    }
});

selectCurrentButton.addEventListener("click", () => {
    setProjectPath(state.browserCurrentPath);
    closePathDialog();
});

async function initializeWorkspace() {
    try {
        const response = await fetch("/api/agent/workspace");
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Cannot load workspace");
        }

        state.workspaceRoot = payload.workspaceRoot;
        const savedPath = localStorage.getItem("myagent.projectPath");
        setProjectPath(savedPath || payload.workspaceRoot);
    } catch (error) {
        projectPath.textContent = error.message;
        appendMessage("error", `项目路径初始化失败：${error.message}`);
    }
}

function setProjectPath(path) {
    state.projectPath = path;
    localStorage.setItem("myagent.projectPath", path);
    projectPath.textContent = path;
}

async function openPathDialog(path) {
    pathDialog.classList.remove("hidden");
    await loadDirectories(path);
}

function closePathDialog() {
    pathDialog.classList.add("hidden");
}

async function loadDirectories(path) {
    directoryList.innerHTML = '<li class="directory-empty">Loading...</li>';
    try {
        const url = path ? `/api/agent/directories?path=${encodeURIComponent(path)}` : "/api/agent/directories";
        const response = await fetch(url);
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Cannot load directories");
        }

        state.browserCurrentPath = payload.currentPath;
        state.browserParentPath = payload.parentPath;
        browserRoot.textContent = payload.workspaceRoot;
        currentFolder.textContent = payload.currentPath;
        parentButton.disabled = !payload.parentPath;
        renderDirectories(payload.directories || []);
    } catch (error) {
        directoryList.innerHTML = "";
        const item = document.createElement("li");
        item.className = "directory-empty";
        item.textContent = error.message;
        directoryList.appendChild(item);
    }
}

function renderDirectories(directories) {
    directoryList.innerHTML = "";
    if (!directories.length) {
        const item = document.createElement("li");
        item.className = "directory-empty";
        item.textContent = "No subfolders";
        directoryList.appendChild(item);
        return;
    }

    directories.forEach((directory) => {
        const item = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "directory-button";
        button.innerHTML = `<span>${escapeHtml(directory.name)}</span>${directory.projectCandidate ? "<strong>Project</strong>" : ""}`;
        button.addEventListener("click", () => loadDirectories(directory.path));
        item.appendChild(button);
        directoryList.appendChild(item);
    });
}

function setBusy(value) {
    state.busy = value;
    sendButton.disabled = value;
    clearButton.disabled = value;
    chooseProjectButton.disabled = value;
    question.disabled = value;
    statusText.textContent = value ? "Working" : "Ready";
}

function appendMessage(type, text) {
    const node = document.createElement("article");
    node.className = `message ${type}`;
    node.innerHTML = renderText(text);
    messages.appendChild(node);
    messages.scrollTop = messages.scrollHeight;
    return node;
}

function renderUsedFiles(files) {
    usedFiles.innerHTML = "";
    if (!files.length) {
        const item = document.createElement("li");
        item.className = "muted";
        item.textContent = "No files yet";
        usedFiles.appendChild(item);
        return;
    }
    files.forEach((file) => {
        const item = document.createElement("li");
        item.textContent = file;
        usedFiles.appendChild(item);
    });
}

function renderText(text) {
    const segments = splitCodeBlocks(text || "");
    return segments.map((segment) => {
        if (segment.type === "code") {
            return `<pre><code>${escapeHtml(segment.content.trim())}</code></pre>`;
        }
        return renderMarkdownBlock(segment.content);
    }).join("");
}

function splitCodeBlocks(text) {
    const segments = [];
    const pattern = /```(?:[a-zA-Z0-9_-]+)?\n?([\s\S]*?)```/g;
    let lastIndex = 0;
    let match;

    while ((match = pattern.exec(text)) !== null) {
        if (match.index > lastIndex) {
            segments.push({
                type: "text",
                content: text.slice(lastIndex, match.index)
            });
        }
        segments.push({
            type: "code",
            content: match[1]
        });
        lastIndex = pattern.lastIndex;
    }

    if (lastIndex < text.length) {
        segments.push({
            type: "text",
            content: text.slice(lastIndex)
        });
    }

    return segments;
}

function renderMarkdownBlock(text) {
    const lines = text.replace(/\r\n/g, "\n").split("\n");
    const html = [];
    let listType = null;

    lines.forEach((rawLine) => {
        const line = rawLine.trim();
        if (!line) {
            closeList();
            return;
        }

        const heading = line.match(/^(#{1,4})\s+(.+)$/);
        if (heading) {
            closeList();
            const level = heading[1].length + 2;
            html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
            return;
        }

        const unordered = line.match(/^[-*]\s+(.+)$/);
        if (unordered) {
            openList("ul");
            html.push(`<li>${renderInlineMarkdown(unordered[1])}</li>`);
            return;
        }

        const ordered = line.match(/^\d+\.\s+(.+)$/);
        if (ordered) {
            openList("ol");
            html.push(`<li>${renderInlineMarkdown(ordered[1])}</li>`);
            return;
        }

        closeList();
        html.push(`<p>${renderInlineMarkdown(line)}</p>`);
    });

    closeList();
    return html.join("");

    function openList(type) {
        if (listType === type) {
            return;
        }
        closeList();
        listType = type;
        html.push(`<${type}>`);
    }

    function closeList() {
        if (!listType) {
            return;
        }
        html.push(`</${listType}>`);
        listType = null;
    }
}

function renderInlineMarkdown(value) {
    return escapeHtml(value)
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
}

function escapeHtml(value) {
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
