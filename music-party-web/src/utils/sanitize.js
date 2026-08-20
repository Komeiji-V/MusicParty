// 共享 HTML 消毒器（M6）：基于 DOMParser 的严格白名单消毒。
// 供 InfoModal / HomePage 的 v-html 使用，防止管理员可控内容中的存储型 XSS。

// 允许的标签/属性白名单（其余一律剥离，仅保留文本内容）
const ALLOWED_TAGS = new Set([
    'A', 'P', 'BR', 'B', 'I', 'EM', 'STRONG', 'U', 'S', 'UL', 'OL', 'LI',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'BLOCKQUOTE', 'CODE', 'PRE',
    'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'IMG', 'HR', 'DIV', 'SPAN',
    'SECTION', 'DETAILS', 'SUMMARY', 'FIGURE', 'FIGCAPTION', 'MARK', 'SMALL', 'SUB', 'SUP'
])
const ALLOWED_ATTRS = new Set([
    'href', 'title', 'src', 'alt', 'width', 'height', 'target', 'rel',
    'align', 'colspan', 'rowspan', 'start', 'type'
])
const SAFE_PROTOCOLS = new Set(['http:', 'https:'])

/**
 * URL 校验（修复控制字符绕过）：
 * 先剥离全部 ASCII 控制字符（如 "java&#10;script:" 经 DOMParser 解码后的换行），
 * 无协议视为相对路径放行；带协议的必须解析后属于 http/https。
 */
const isSafeUrl = (raw) => {
    if (raw == null) return false
    const value = raw.replace(/[\u0000-\u001F\u007F]/g, '').trim()
    if (!value) return false
    // 协议相对 URL（//host/path）与相对路径（无协议）安全
    if (/^\/\//.test(value)) return true
    if (!/^[a-z][a-z0-9+.-]*:/i.test(value)) return true
    try {
        const u = new URL(value, 'http://local.invalid')
        return SAFE_PROTOCOLS.has(u.protocol)
    } catch (e) {
        return false
    }
}

// 基于 DOMParser 的严格白名单消毒（正则方案可被无引号事件属性/实体编码绕过）
export const sanitizeHtml = (dirty) => {
    if (!dirty) return ''
    const doc = new DOMParser().parseFromString(dirty, 'text/html')
    const elements = []
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT)
    while (walker.nextNode()) elements.push(walker.currentNode)

    for (const el of elements) {
        if (!ALLOWED_TAGS.has(el.tagName)) {
            el.replaceWith(...el.childNodes) // 剥离危险标签，保留其文本内容
            continue
        }
        for (const attr of [...el.attributes]) {
            const name = attr.name.toLowerCase()
            const value = attr.value
            // 删除所有事件处理器属性
            if (name.startsWith('on')) {
                el.removeAttribute(attr.name)
                continue
            }
            // 删除白名单之外的属性
            if (!ALLOWED_ATTRS.has(name)) {
                el.removeAttribute(attr.name)
                continue
            }
            // URL 属性仅允许 http/https/相对路径（含协议相对）；data: 一律拒绝
            if (name === 'href' || name === 'src') {
                if (!isSafeUrl(value)) {
                    el.removeAttribute(attr.name)
                }
            }
        }
    }
    // 链接安全增强：rel 追加 noopener nofollow（防 window.opener 滥用与 SEO 传递）
    for (const a of doc.querySelectorAll('a')) {
        const rel = new Set((a.getAttribute('rel') || '').split(/\s+/).filter(Boolean))
        rel.add('noopener')
        rel.add('nofollow')
        a.setAttribute('rel', [...rel].join(' '))
    }
    // 兜底清理（如实体编码残留的 script 结构）
    return doc.body.innerHTML
        .replace(/<\s*script[\s\S]*?<\s*\/\s*script\s*>/gi, '')
        .replace(/<\s*iframe[\s\S]*?<\s*\/\s*iframe\s*>/gi, '')
}
