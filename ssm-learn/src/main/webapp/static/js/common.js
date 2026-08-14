/**
 * 公共脚本：
 * 1) ctx：项目上下文路径。本模板所有静态资源均使用根绝对路径（/static/、/dashboard.html），
 *         因此必须部署在根上下文（contextPath="/"），ctx 固定为空字符串。
 * 2) request：基于 fetch 的轻量 AJAX 封装，统一走 JSON
 */
var ctx = '';

/**
 * 发起请求
 * @param {string} url     接口地址（不含 ctx）
 * @param {object} options { method, data }
 * @returns {Promise<any>} 解析为 JSON 响应
 */
function request(url, options) {
    options = options || {};
    var method = options.method || 'GET';
    var init = {
        method: method,
        headers: {'Content-Type': 'application/json'}
    };
    if (options.data) {
        init.body = JSON.stringify(options.data);
    }
    return fetch(ctx + url, init).then(function (resp) {
        return resp.json();
    });
}
