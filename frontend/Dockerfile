FROM node:alpine

RUN npm install -g serve

COPY dist /app

EXPOSE 80

CMD ["serve", "-s", "/app", "-l", "80"]
