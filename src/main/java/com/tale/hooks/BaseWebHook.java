package com.tale.hooks;

import com.blade.ioc.annotation.Bean;
import com.blade.kit.StringKit;
import com.blade.kit.UUID;
import com.blade.mvc.hook.Signature;
import com.blade.mvc.hook.WebHook;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.tale.init.TaleConst;
import com.tale.model.dto.Types;
import com.tale.model.entity.Users;
import com.tale.utils.MapCache;
import com.tale.utils.TaleUtils;
import lombok.extern.slf4j.Slf4j;

@Bean
@Slf4j
public class BaseWebHook implements WebHook {

    private MapCache cache = MapCache.single();

    @Override
    public boolean before(Signature signature) {
        Request request = signature.request();
        Response response = signature.response();

        String uri = request.uri();
        String ip = request.address();

        // 禁止该ip访问
        if(TaleConst.BLOCK_IPS.contains(ip)){
            response.text("You have been banned, brother");
            return false;
        }

        log.info("UserAgent: {}", request.userAgent());
        log.info("用户访问地址: {}, 来路地址: {}", uri, ip);

        if (uri.startsWith("/static")) {
            return true;
        }

        if (!TaleConst.INSTALL && !uri.startsWith("/install")) {
            response.redirect("/install");
            return false;
        }

        if (TaleConst.INSTALL) {
            Users user = TaleUtils.getLoginUser();
            if (null == user) {
                Integer uid = TaleUtils.getCookieUid(request);
                if (null != uid) {
                    user = new Users().find(uid);
                    request.session().attribute(TaleConst.LOGIN_SESSION_KEY, user);
                }
            }

            if(uri.startsWith("/admin") && !uri.startsWith("/admin/login")){
                if(null == user){
                    response.redirect("/admin/login");
                    return false;
                }
                request.attribute("plugin_menus", TaleConst.plugin_menus);
            }
        }
        String method = request.method();
        if(method.equals("GET")){
            String csrf_token = UUID.UU64();
            // 默认存储20分钟
            int timeout = TaleConst.BCONF.getInt("app.csrf-token-timeout", 20) * 60;
            cache.hset(Types.CSRF_TOKEN, csrf_token, uri, timeout);
            request.attribute("_csrf_token", csrf_token);
        }
        return true;
    }

    @Override
    public boolean after(Signature signature) {
        Request request = signature.request();
        String _csrf_token = request.attribute("del_csrf_token");
        if(StringKit.isNotBlank(_csrf_token)){
            // 移除本次token
            cache.hdel(Types.CSRF_TOKEN, _csrf_token);
        }
        return true;
    }
}
