### 1. Install Ollama
### 2. Run deepseek model (or llama2:latest) locally by running below command
    >> ollama run deepseek-r1:1.5b
### 3. Start this spring boot application
### 4. Load the pdf content (file name configured in code) by calling http://localhost:8080/api/v1/load url from browser.
  This need to done only once. Content will be saved in DB.
### 4. Test application by asking qustions in below format from browser:
  http://localhost:8080/api/v1/chat?userQuery=<YOUR QUESTION>
