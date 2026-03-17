# 构建阶段
FROM node:alpine as builder
WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmjs.org
RUN npm install
COPY . .
RUN npm run build

# 运行阶段
FROM node:alpine
RUN npm install -g serve
COPY --from=builder /app/dist /app
COPY serve.json /app/serve.json
EXPOSE 80
CMD ["serve", "/app", "-l", "80", "--config", "/app/serve.json"]
