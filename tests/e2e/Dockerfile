FROM node:22-alpine
WORKDIR /workspace
RUN npm install --prefix /opt/emerald-postman-runner newman@6
ENV EMERALD_NEWMAN_HOME=/opt/emerald-postman-runner
COPY scripts/run-postman-option-b.cjs scripts/run-postman-option-b.cjs
COPY scripts/e2e/run-e2e.cjs scripts/e2e/run-e2e.cjs
COPY postman/Emerald-Housing-Option-B.postman_collection.json postman/Emerald-Housing-Option-B.postman_collection.json
COPY postman/Emerald-Housing-Option-B.postman_environment.json postman/Emerald-Housing-Option-B.postman_environment.json
CMD ["node", "scripts/e2e/run-e2e.cjs"]
