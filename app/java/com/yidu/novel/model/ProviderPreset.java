package com.yidu.novel.model;

import java.util.ArrayList;
import java.util.List;

public class ProviderPreset {
    public final String id;
    public final String name;
    public final String vendor;
    public final String protocol;
    public final String baseUrl;
    public final String endpointPath;
    public final String model;
    public final String consoleUrl;
    public final String note;
    public final String[] models;
    public final boolean needsKey;

    public ProviderPreset(String id, String name, String vendor, String protocol, String baseUrl,
                          String endpointPath, String model, String consoleUrl, String note,
                          String[] models, boolean needsKey) {
        this.id = id;
        this.name = name;
        this.vendor = vendor;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.endpointPath = endpointPath;
        this.model = model;
        this.consoleUrl = consoleUrl;
        this.note = note;
        this.models = models;
        this.needsKey = needsKey;
    }

    public static List<ProviderPreset> all() {
        List<ProviderPreset> list = new ArrayList<>();
        list.add(new ProviderPreset(
                "siliconflow", "硅基流动 SiliconFlow", "SiliconFlow", "openai",
                "https://api.siliconflow.cn/v1", "/chat/completions",
                "deepseek-ai/DeepSeek-V3.2", "https://cloud.siliconflow.cn/account/ak",
                "国内主流模型聚合平台，OpenAI 兼容接口。模型名需与模型广场完全一致，例如 deepseek-ai/DeepSeek-V3.2。",
                new String[]{"deepseek-ai/DeepSeek-V3.2", "deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen3-32B", "Qwen/Qwen2.5-72B-Instruct"},
                true));
        list.add(new ProviderPreset(
                "a6api", "A6API 中转站", "a6api.vip", "openai",
                "https://www.a6api.vip/v1", "/chat/completions",
                "gpt-4o-mini", "https://www.a6api.vip/?sort=cache_hit_desc#/console/tokens",
                "OpenAI 兼容中转平台，控制台令牌页面见上。Base URL 可能有分组差异，若 404 请以控制台提供的 Base URL 为准；接口路径保留 /chat/completions。",
                new String[]{"gpt-4o-mini", "gpt-4o", "claude-sonnet-4-5", "deepseek-chat", "deepseek-reasoner"},
                true));
        list.add(new ProviderPreset(
                "deepseek", "DeepSeek 官方", "DeepSeek", "openai",
                "https://api.deepseek.com", "/chat/completions",
                "deepseek-chat", "https://platform.deepseek.com/api_keys",
                "支持官方 thinking 思考模式开关与 low / high / max 推理强度，详见思考模式文档。此开关同样可开启后用于中转站。",
                new String[]{"deepseek-chat", "deepseek-reasoner", "deepseek-flash", "deepseek-v4-pro"},
                true));
        list.add(new ProviderPreset(
                "openai", "OpenAI", "OpenAI", "openai",
                "https://api.openai.com/v1", "/chat/completions",
                "gpt-4o-mini", "https://platform.openai.com/api-keys",
                "标准 OpenAI Chat Completions 接口，支持自定义请求头。",
                new String[]{"gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1"},
                true));
        list.add(new ProviderPreset(
                "anthropic", "Anthropic Claude", "Anthropic", "anthropic",
                "https://api.anthropic.com/v1", "/messages",
                "claude-sonnet-4-5", "https://console.anthropic.com/settings/keys",
                "使用 Anthropic Messages 原生协议，App 自动适配请求与流式响应。",
                new String[]{"claude-sonnet-4-5", "claude-haiku-4-5", "claude-opus-4-1"},
                true));
        list.add(new ProviderPreset(
                "gemini", "Google Gemini", "Google", "gemini",
                "https://generativelanguage.googleapis.com/v1beta", "",
                "gemini-2.5-flash", "https://aistudio.google.com/apikey",
                "使用 Gemini 原生协议，路径由 App 自动拼接为 models/{model}:streamGenerateContent。",
                new String[]{"gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"},
                true));
        list.add(new ProviderPreset(
                "moonshot", "Moonshot Kimi", "Moonshot AI", "openai",
                "https://api.moonshot.cn/v1", "/chat/completions",
                "moonshot-v1-8k", "https://platform.moonshot.cn/console/api-keys",
                "Kimi 开放平台，OpenAI 兼容。",
                new String[]{"moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2-0905-preview"},
                true));
        list.add(new ProviderPreset(
                "zhipu", "智谱 GLM", "Zhipu AI", "openai",
                "https://open.bigmodel.cn/api/paas/v4", "/chat/completions",
                "glm-4-flash", "https://bigmodel.cn/usercenter/apikeys",
                "智谱开放平台，OpenAI 兼容。",
                new String[]{"glm-4-flash", "glm-4-plus", "glm-4.5"},
                true));
        list.add(new ProviderPreset(
                "qwen", "阿里云百炼 Qwen", "Alibaba Cloud", "openai",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions",
                "qwen-plus", "https://bailian.console.aliyun.com/?apiKey=1",
                "百炼 OpenAI 兼容模式，使用 DashScope API Key。",
                new String[]{"qwen-plus", "qwen-turbo", "qwen-max", "qwen-long"},
                true));
        list.add(new ProviderPreset(
                "volcengine", "火山方舟 豆包", "Volcengine", "openai",
                "https://ark.cn-beijing.volces.com/api/v3", "/chat/completions",
                "doubao-seed-1-6-250615", "https://console.volcengine.com/ark",
                "方舟 OpenAI 兼容接口。模型名通常需填写推理接入点 ID（ep-...）或平台模型名。",
                new String[]{"doubao-seed-1-6-250615", "doubao-1-5-pro-32k-250115"},
                true));
        list.add(new ProviderPreset(
                "baidu", "百度千帆", "Baidu", "openai",
                "https://qianfan.baidubce.com/v2", "/chat/completions",
                "ernie-4.5-8k-preview", "https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application",
                "千帆 v2 OpenAI 兼容接口，使用千帆 API Key。",
                new String[]{"ernie-4.5-8k-preview", "ernie-4.0-8k", "ernie-speed-128k"},
                true));
        list.add(new ProviderPreset(
                "hunyuan", "腾讯混元", "Tencent", "openai",
                "https://api.hunyuan.cloud.tencent.com/v1", "/chat/completions",
                "hunyuan-turbos-latest", "https://console.cloud.tencent.com/hunyuan/api-key",
                "腾讯混元 OpenAI 兼容接口。",
                new String[]{"hunyuan-turbos-latest", "hunyuan-t1-latest", "hunyuan-lite"},
                true));
        list.add(new ProviderPreset(
                "minimax", "MiniMax", "MiniMax", "openai",
                "https://api.minimax.chat/v1", "/chat/completions",
                "MiniMax-Text-01", "https://platform.minimaxi.com/user-center/basic-information/interface-key",
                "MiniMax 文本模型 OpenAI 兼容接口。",
                new String[]{"MiniMax-Text-01", "abab6.5s-chat"},
                true));
        list.add(new ProviderPreset(
                "openrouter", "OpenRouter", "OpenRouter", "openai",
                "https://openrouter.ai/api/v1", "/chat/completions",
                "openai/gpt-4o-mini", "https://openrouter.ai/settings/keys",
                "聚合多家模型，一个 Key 可用大量模型；模型名使用 provider/model 格式。建议在自定义请求头填写 HTTP-Referer。",
                new String[]{"openai/gpt-4o-mini", "anthropic/claude-sonnet-4.5", "google/gemini-2.5-flash", "deepseek/deepseek-chat"},
                true));
        list.add(new ProviderPreset(
                "groq", "Groq", "Groq", "openai",
                "https://api.groq.com/openai/v1", "/chat/completions",
                "llama-3.3-70b-versatile", "https://console.groq.com/keys",
                "高速推理平台，OpenAI 兼容。",
                new String[]{"llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b"},
                true));
        list.add(new ProviderPreset(
                "ollama", "Ollama 本地模型", "Local", "openai",
                "http://127.0.0.1:11434/v1", "/chat/completions",
                "qwen2.5:7b", "https://ollama.com/download",
                "本机/局域网部署。手机访问电脑时请将 127.0.0.1 改为电脑局域网 IP，并允许 Ollama 监听外部地址。",
                new String[]{"qwen2.5:7b", "llama3.1:8b", "gemma3:4b"},
                false));
        list.add(new ProviderPreset(
                "custom", "自定义 API", "Custom", "openai",
                "", "/chat/completions",
                "", "",
                "兼容任意 OpenAI Chat Completions 接口，也可切换为 Anthropic 或 Gemini 原生协议；适合不常见的 AI 模型与自建网关。",
                new String[0],
                false));
        return list;
    }

    public static ProviderPreset byId(String id) {
        for (ProviderPreset preset : all()) {
            if (preset.id.equals(id)) return preset;
        }
        return all().get(all().size() - 1);
    }

    public static String protocolName(String protocol) {
        if ("anthropic".equals(protocol)) return "Anthropic";
        if ("gemini".equals(protocol)) return "Gemini";
        return "OpenAI 兼容";
    }
}
