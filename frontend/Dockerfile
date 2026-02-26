FROM nginx:alpine

COPY dist /usr/share/nginx/html

# 使用默认的 nginx 配置，仅提供静态文件服务
RUN rm /etc/nginx/conf.d/default.conf
RUN echo 'server { listen 80; root /usr/share/nginx/html; index index.html; location / { try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
