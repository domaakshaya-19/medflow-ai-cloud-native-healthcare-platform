"""Generate reviewable deployment templates. Does not start Docker or contact a cluster/cloud."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parent.parent
DOMAINS=['auth','patient','doctor','appointment','medical_records','lab','prescription','pharmacy','nutrition','billing','emergency','notification']
PORTS={'discovery-server':8761,'auth-service':8081,'patient-service':8082,'doctor-service':8083,'appointment-service':8084,'medical-records-service':8085,'lab-service':8086,'prescription-service':8087,'pharmacy-service':8088,'nutrition-service':8089,'billing-service':8090,'emergency-service':8091,'notification-service':8092,'ai-service':8093,'api-gateway':8080}
def write(path,data):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n')
compose={'name':'medflow-demo-isolated','services':{},'volumes':{},'networks':{'medflow':{}},'secrets':{'jwt':{'environment':'DOCKER_JWT_SECRET'},'bootstrap':{'environment':'DOCKER_BOOTSTRAP_PASSWORD'}}}
required=['DOCKER_JWT_SECRET','DOCKER_BOOTSTRAP_PASSWORD']
for domain in DOMAINS:
    name=domain.replace('_','-');prefix=domain.upper();db=name+'-db';role='medflow_'+domain
    for kind in ['app','admin']:
        secret=domain+'-'+kind;env='DOCKER_'+prefix+'_'+kind.upper()+'_PASSWORD';required.append(env);compose['secrets'][secret]={'environment':env}
    compose['volumes'][domain+'-data']={}
    compose['services'][db]={'image':'postgres:16-alpine','environment':{'POSTGRES_USER':'postgres','POSTGRES_DB':role,'POSTGRES_PASSWORD_FILE':'/run/secrets/admin-password','APP_DB_USER':role,'APP_DB_PASSWORD_FILE':'/run/secrets/app-password'},'secrets':[{'source':domain+'-admin','target':'admin-password'},{'source':domain+'-app','target':'app-password'}],'volumes':[domain+'-data:/var/lib/postgresql/data','./init-app-role.sh:/docker-entrypoint-initdb.d/01-app-role.sh:ro'],'networks':['medflow'],'healthcheck':{'test':['CMD-SHELL','pg_isready -U postgres -d '+role],'interval':'5s','timeout':'3s','retries':20}}
for name,port in PORTS.items():
    service={'build':{'context':'..','dockerfile':'deploy/Dockerfile.backend','args':{'SERVICE':name}},'networks':['medflow'],'environment':{'SERVER_PORT':str(port),'EUREKA_SERVER_URL':'http://discovery-server:8761/eureka','EUREKA_INSTANCE_PREFER_IP_ADDRESS':'true','SPRING_CONFIG_IMPORT':'optional:configtree:/run/secrets/','API_GATEWAY_URL':'http://api-gateway:8080','FRONTEND_URL':'http://localhost:15173','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'4','SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE':'1'},'secrets':[],'depends_on':{},'deploy':{'resources':{'limits':{'memory':'512M'}}}}
    domain=name.removesuffix('-service').replace('-','_')
    if name!='discovery-server':service['depends_on']['discovery-server']={'condition':'service_started'}
    if domain in DOMAINS:
        prefix=domain.upper();service['environment'].update({prefix+'_DB_URL':'jdbc:postgresql://'+domain.replace('_','-')+'-db:5432/medflow_'+domain,prefix+'_DB_USER':'medflow_'+domain,prefix+'_SERVICE_PORT':str(port)})
        service['secrets'] += [{'source':domain+'-app','target':prefix+'_DB_PASSWORD'}]
        service['depends_on'][domain.replace('_','-')+'-db']={'condition':'service_healthy'}
    if name not in ['discovery-server','api-gateway']:service['secrets'].append({'source':'jwt','target':'JWT_SECRET'})
    if name=='auth-service':service['environment']['ADMIN_BOOTSTRAP_EMAIL']='admin@example.test';service['secrets'].append({'source':'bootstrap','target':'ADMIN_BOOTSTRAP_PASSWORD'})
    if name=='api-gateway':service['ports']=['127.0.0.1:18080:8080']
    if name=='discovery-server':service['ports']=['127.0.0.1:18761:8761']
    compose['services'][name]=service
compose['services']['frontend']={'build':{'context':'..','dockerfile':'deploy/Dockerfile.frontend','args':{'VITE_API_URL':'http://localhost:18080'}},'ports':['127.0.0.1:15173:80'],'networks':['medflow'],'depends_on':['api-gateway']}
write('deploy/compose.dev.json',compose)
write('deploy/required-secret-environment.json',{'requiredNames':required,'note':'Use unique secrets generated for this isolated stack. Never reuse/reset native credentials; never print compose config with secrets.'})
items=[{'apiVersion':'v1','kind':'Namespace','metadata':{'name':'medflow-demo'}}]
def resource(kind,name,spec=None,**kwargs):
    r={'apiVersion':'apps/v1' if kind in ['Deployment','StatefulSet'] else 'v1','kind':kind,'metadata':{'name':name,'namespace':'medflow-demo'}}
    if spec is not None:r['spec']=spec
    r.update(kwargs);items.append(r);return r
resource('ConfigMap','database-init',data={'01-app-role.sh':(ROOT/'deploy/init-app-role.sh').read_text()})
for domain in DOMAINS:
    name=domain.replace('_','-');db=name+'-db';role='medflow_'+domain;secret='medflow-'+name+'-credentials'
    resource('Service',db,{'selector':{'app':db},'ports':[{'port':5432,'targetPort':5432}]})
    resource('StatefulSet',db,{'serviceName':db,'replicas':1,'selector':{'matchLabels':{'app':db}},'template':{'metadata':{'labels':{'app':db}},'spec':{'containers':[{'name':'postgres','image':'postgres:16-alpine','ports':[{'containerPort':5432}],'env':[{'name':k,'value':v} for k,v in {'POSTGRES_USER':'postgres','POSTGRES_DB':role,'POSTGRES_PASSWORD_FILE':'/run/secrets/admin-password','APP_DB_USER':role,'APP_DB_PASSWORD_FILE':'/run/secrets/app-password'}.items()],'volumeMounts':[{'name':'data','mountPath':'/var/lib/postgresql/data'},{'name':'credentials','mountPath':'/run/secrets','readOnly':True},{'name':'init','mountPath':'/docker-entrypoint-initdb.d','readOnly':True}],'resources':{'requests':{'memory':'128Mi','cpu':'100m'},'limits':{'memory':'384Mi','cpu':'500m'}},'readinessProbe':{'exec':{'command':['pg_isready','-U','postgres','-d',role]},'periodSeconds':5}}],'volumes':[{'name':'credentials','secret':{'secretName':secret}},{'name':'init','configMap':{'name':'database-init'}}]}},'volumeClaimTemplates':[{'metadata':{'name':'data'},'spec':{'accessModes':['ReadWriteOnce'],'resources':{'requests':{'storage':'2Gi'}}}}]})
for name,port in PORTS.items():
    domain=name.removesuffix('-service').replace('-','_');env={'SERVER_PORT':str(port),'EUREKA_SERVER_URL':'http://discovery-server:8761/eureka','EUREKA_INSTANCE_PREFER_IP_ADDRESS':'true','API_GATEWAY_URL':'http://api-gateway:8080','FRONTEND_URL':'https://medflow.example.test','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx256m','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'4','SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE':'1'}
    secrets=[]
    if domain in DOMAINS:
        prefix=domain.upper();env.update({prefix+'_DB_URL':'jdbc:postgresql://'+domain.replace('_','-')+'-db:5432/medflow_'+domain,prefix+'_DB_USER':'medflow_'+domain,prefix+'_SERVICE_PORT':str(port)})
        secrets.append({'name':prefix+'_DB_PASSWORD','valueFrom':{'secretKeyRef':{'name':'medflow-'+domain.replace('_','-')+'-credentials','key':'app-password'}}})
    if name not in ['discovery-server','api-gateway']:secrets.append({'name':'JWT_SECRET','valueFrom':{'secretKeyRef':{'name':'medflow-signing','key':'JWT_SECRET'}}})
    if name=='auth-service':env['ADMIN_BOOTSTRAP_EMAIL']='admin@example.test';secrets.append({'name':'ADMIN_BOOTSTRAP_PASSWORD','valueFrom':{'secretKeyRef':{'name':'medflow-signing','key':'bootstrap-password'}}})
    if name=='ai-service':secrets.append({'name':'GEMINI_API_KEY','valueFrom':{'secretKeyRef':{'name':'medflow-provider','key':'GEMINI_API_KEY','optional':True}}})
    resource('Service',name,{'selector':{'app':name},'ports':[{'port':port,'targetPort':port}]})
    resource('Deployment',name,{'replicas':1,'selector':{'matchLabels':{'app':name}},'template':{'metadata':{'labels':{'app':name}},'spec':{'securityContext':{'runAsNonRoot':True,'runAsUser':10001,'runAsGroup':10001},'containers':[{'name':name,'image':'ghcr.io/your-org/medflow-'+name+':reviewed-sha-required','ports':[{'containerPort':port}],'env':[{'name':k,'value':v} for k,v in env.items()]+secrets,'resources':{'requests':{'memory':'256Mi','cpu':'100m'},'limits':{'memory':'512Mi','cpu':'1000m'}},'startupProbe':{'httpGet':{'path':'/actuator/health/liveness','port':port},'failureThreshold':36,'periodSeconds':5},'readinessProbe':{'httpGet':{'path':'/actuator/health/readiness','port':port},'periodSeconds':5,'timeoutSeconds':3},'livenessProbe':{'httpGet':{'path':'/actuator/health/liveness','port':port},'periodSeconds':15,'timeoutSeconds':3},'securityContext':{'allowPrivilegeEscalation':False,'readOnlyRootFilesystem':True,'capabilities':{'drop':['ALL']}},'volumeMounts':[{'name':'tmp','mountPath':'/tmp'}]}],'volumes':[{'name':'tmp','emptyDir':{}}]}}})
resource('Service','frontend',{'selector':{'app':'frontend'},'ports':[{'port':80,'targetPort':80}]})
resource('Deployment','frontend',{'replicas':1,'selector':{'matchLabels':{'app':'frontend'}},'template':{'metadata':{'labels':{'app':'frontend'}},'spec':{'containers':[{'name':'frontend','image':'ghcr.io/your-org/medflow-frontend:reviewed-sha-required','ports':[{'containerPort':80}],'resources':{'requests':{'memory':'32Mi','cpu':'50m'},'limits':{'memory':'128Mi','cpu':'250m'}},'readinessProbe':{'httpGet':{'path':'/health','port':80}}}]}}})
items.append({'apiVersion':'networking.k8s.io/v1','kind':'Ingress','metadata':{'name':'medflow','namespace':'medflow-demo'},'spec':{'tls':[{'hosts':['medflow.example.test'],'secretName':'medflow-tls'}],'rules':[{'host':'medflow.example.test','http':{'paths':[{'path':'/api','pathType':'Prefix','backend':{'service':{'name':'api-gateway','port':{'number':8080}}}},{'path':'/','pathType':'Prefix','backend':{'service':{'name':'frontend','port':{'number':80}}}}]}}]}})
write('deploy/k8s/resources.json',{'apiVersion':'v1','kind':'List','items':items})
write('deploy/k8s/required-secret-keys.json',{'namespace':'medflow-demo','secrets':{**{'medflow-'+d.replace('_','-')+'-credentials':['admin-password','app-password'] for d in DOMAINS},'medflow-signing':['JWT_SECRET','bootstrap-password'],'medflow-provider':['GEMINI_API_KEY (optional)'],'medflow-tls':['tls.crt','tls.key']},'note':'References only: actual secrets must be supplied outside Git. These templates are not a deployment claim.'})
write('deploy/monitoring/prometheus.example.json',{'global':{'scrape_interval':'30s'},'scrape_configs':[{'job_name':'medflow-actuator-example','metrics_path':'/actuator/prometheus','authorization':{'credentials_file':'/run/secrets/metrics-token'},'static_configs':[{'targets':[name+':'+str(port) for name,port in PORTS.items()]}]}]})
write('deploy/monitoring/grafana-dashboard.json',{'title':'MedFlow service availability (requires configured metrics)','schemaVersion':39,'version':1,'panels':[{'id':1,'type':'stat','title':'Scrape availability','gridPos':{'x':0,'y':0,'w':24,'h':8},'targets':[{'expr':'up{job="medflow-actuator-example"}','legendFormat':'{{instance}}'}]}]})
print(f'Generated isolated Compose topology and {len(items)} Kubernetes resources; no stack or cloud resources started.')
