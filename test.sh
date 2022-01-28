restart_services() {
  systemctl daemon-reload
  while IFS= read -r line
    do
      for svc in ${line}; do
        systemctl is-active --quiet "$svc"
        if [ $? -eq 0 ]; then
          systemctl restart "$svc"
          # TODO: configure sleep time - figure it out /retry?
          # sleep 5
          systemctl is-active --quiet "$svc"
          if [ $? -gt 0 ]; then
            echo "Failed to start "$svc" service. Patch Failed. Retrying again" >> $logfile 2>&1
            if [ $backuprestored == 0 ]; then
              restore_backup
            fi
            patchfailed=1
            break
          fi
        fi
      done	
      if [ $patchfailed == 1 ]; then
        return
      fi
    done < "$svcfile"
}

restore_backup() {
	backuprestores=1
	restart_services
}

patchfailed=0
svcfile='./svc'
restart_services
