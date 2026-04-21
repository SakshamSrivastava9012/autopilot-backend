FROM node:{{RUNTIME_VERSION}}-alpine

WORKDIR /app

# Enable standalone mode output
ENV NEXT_TELEMETRY_DISABLED 1

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy source code
COPY . .

# Build application
RUN {{BUILD_COMMAND}}

# Expose port
EXPOSE {{PORT}}

# Start application
CMD {{START_COMMAND}}
