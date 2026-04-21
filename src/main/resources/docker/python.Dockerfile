FROM python:{{RUNTIME_VERSION}}-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE {{PORT}}

CMD {{START_COMMAND}}