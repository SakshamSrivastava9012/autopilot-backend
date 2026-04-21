FROM node:{{RUNTIME_VERSION}}-alpine

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy source
COPY . .

# Build React app
RUN {{BUILD_COMMAND}}

# Install serve to run the application
RUN npm install -g serve

# Expose port
EXPOSE {{PORT}}

# Start the application using serve
CMD {{START_COMMAND}}
