import importlib.util,json,tempfile,unittest,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('votex_selfhost_package',root/'scripts/package-selfhost.py')
package=importlib.util.module_from_spec(spec);spec.loader.exec_module(package)

class RelayPackageAcceptance(unittest.TestCase):
    def test_rejects_the_stale_catalog_that_blocked_new_remote_appearances(self):
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'relay.jar'
            current=b'[{"id":"new-fitted-item"}]'
            for catalog,client,identity in [(b'[]',False,'votex_social_relay'),(current,True,'votex_social_relay'),(current,False,'votex_client'),(current,False,'votex_social_relay')]:
                with zipfile.ZipFile(file,'w') as jar:
                    jar.writestr('fabric.mod.json',json.dumps({'id':identity,'environment':'server','version':'fixture'}))
                    jar.writestr('assets/votex_client/catalog/index.json',catalog)
                    if client:jar.writestr('dev/votex/client/Forbidden.class',b'fixture')
                if catalog!=current or client or identity!='votex_social_relay':
                    with self.assertRaises(RuntimeError):package.validate_relay(file,current)
                else:self.assertEqual(package.validate_relay(file,current)['id'],'votex_social_relay')

if __name__=='__main__':unittest.main()
