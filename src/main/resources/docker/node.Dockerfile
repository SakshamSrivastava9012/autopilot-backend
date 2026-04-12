FROM node:20-alpine

WORKDIR /app

COPY package*.json ./

# install deps
RUN npm install

COPY . .

# 🔥 ALWAYS CLEAN OLD BUILD
RUN rm -rf .next

# 🔥 BUILD WITHOUT ESLINT (CRITICAL)
RUN npx next build --no-lint

EXPOSE {{PORT}}

# 🔥 ENSURE SERVER BINDS PROPERLY
CMD ["npm","start","--","-H","0.0.0.0"]