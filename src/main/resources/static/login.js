var httpRequest = new XMLHttpRequest();//第一步：建立所需的对象
httpRequest.open('GET', './wx/login/qrCode', true);//第二步：打开连接  将请求参数写在url中  ps:"./Ptest.php?name=test&nameone=testone"
httpRequest.send();//第三步：发送请求  将请求参数写在URL中
/**
 * 获取数据后的处理程序
 */
httpRequest.onreadystatechange = function () {
    if (httpRequest.readyState == 4 && httpRequest.status == 201) {
        document.getElementById('qrCode').src = httpRequest.response;
        var ws = null;
        var host;
        if (window.location.protocol == 'http:') {
            host = 'ws://';
        } else {
            host = 'wss://';
        }
        host += window.location.host + '/wx/' + httpRequest.getResponseHeader("uuid");

        if ('WebSocket' in window) {
            ws = new WebSocket(host);
        } else if ('MozWebSocket' in window) {
            ws = new MozWebSocket(host);
        } else {
            alert("该浏览器不支持WebSocket！");
        //    return;
        }

        ws.onerror = function () {
            setMessageInnerHTML("连接出错");
        };

        ws.onopen = function(event){
            console.log("open...");
            setMessageInnerHTML("扫码登录");
        };

        ws.onmessage = function (event) {
            console.log(event.data);
            if (event.data instanceof Blob) {
                var reader = new FileReader()
                reader.onload = function(eve) {
                    if(eve.target.readyState == FileReader.DONE){
                        document.getElementById('qrCode').src = reader.result;
                    }
                }
                reader.readAsDataURL(event.data);
            } else {
                setMessageInnerHTML(event.data);
            }
        };

        ws.onclose = function () {
            setMessageInnerHTML("连接关闭");
        };

        window.onbeforeunload = function () {
            ws.close();
        };

        function setMessageInnerHTML(innerHTML) {
            document.getElementById('message').innerHTML += innerHTML + '<br/>';
        }
    }
};