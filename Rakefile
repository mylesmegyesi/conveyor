
def exec_in(dir, command)
  sh "cd #{dir} && #{command}"
end

def clean(dir)
  rm_script = 'rm -rf lib target pom.xml .lein-deps-sum .sass-cache checkouts'
  lein_task(dir, 'clean')
  exec_in(dir, rm_script)
  sh rm_script
end

def deps(dir)
  lein_task(dir, 'deps')
end

def jar_spec(dir)
  lein_task(dir, 'with-profile dev uberjar')
  exec_in(dir, "java -jar target/#{dir}-0.2.1-standalone.jar")
end

def spec(dir)
  lein_task(dir, 'spec')
end

def _install(dir)
  lein_task(dir, 'install')
end

def deploy(dir)
  lein_task(dir, 'deploy clojars')
end

def ci?
  ENV['CI'] == '1'
end

def lein_bin
  ci? ? 'lein2' : 'lein'
end

def lein_task(dir, task)
  exec_in(dir, "#{lein_bin} #{task}")
end

def package(name, dependencies)
  desc "Clean #{name}"
  task :clean do
    clean(name)
  end

  desc "Gather dependencies for #{name}"
  task :deps do
    dependencies.each do |dep|
      _install(dep)
    end
  end

  desc "Install #{name}"
  task :install do
    _install(name)
  end

  desc "Run #{name} specs"
  task :spec => [:clean, :deps] do
    spec(name)
    jar_spec(name)
  end

  desc "Deploy #{name}"
  task :deploy do
    deploy(name)
  end
end

namespace :conveyor do
  package('conveyor', [])
end

namespace 'conveyor-sass' do
  package('conveyor-sass', %w{conveyor})
end

namespace 'conveyor-compass' do
  package('conveyor-compass', %w{conveyor conveyor-sass})
end

namespace 'conveyor-coffeescript' do
  package('conveyor-coffeescript', %w{conveyor})
end

namespace 'conveyor-closure' do
  package('conveyor-closure', %w{conveyor})
end

namespace 'conveyor-jst' do
  package('conveyor-jst', %w{conveyor})
end

namespace 'conveyor-clojurescript' do
  package('conveyor-clojurescript', %w{conveyor})
end

PROJECTS = %w(conveyor conveyor-sass conveyor-compass conveyor-coffeescript conveyor-closure conveyor-jst conveyor-clojurescript)

def create_task_for_all(task_name)
  task task_name => PROJECTS.map {|project| "#{project}:#{task_name}"}
end

desc 'Run the specs for conveyor'
create_task_for_all(:spec)

desc 'Clean all conveyor projects'
create_task_for_all(:clean)

desc 'Install all conveyor projects'
create_task_for_all(:install)

desc 'Deploy all conveyor projects'
create_task_for_all(:deploy)

task :default => :spec

