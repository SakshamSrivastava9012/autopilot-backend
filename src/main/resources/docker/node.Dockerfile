FROM node:{{RUNTIME_VERSION}}-alpine

WORKDIR /app

COPY package*.json ./

RUN npm install

COPY . .

RUN {{BUILD_COMMAND}}

EXPOSE {{PORT}}

CMD {{START_COMMAND}}