-- Execute somente depois que inaciooofc@gmail.com tiver criado e confirmado
-- sua conta pelo fluxo de cadastro do aplicativo Retroboy Arcade.
-- Rode este script no SQL Editor do projeto Supabase como proprietário do projeto.
-- Nenhuma senha é armazenada neste arquivo.

do $$
begin
  if not exists (
    select 1
    from auth.users
    where lower(email) = lower('inaciooofc@gmail.com')
  ) then
    raise exception 'A conta developer ainda não existe. Cadastre e confirme o e-mail no aplicativo primeiro.';
  end if;
end
$$;

insert into public.user_roles (user_id, role)
select id, 'developer'
from auth.users
where lower(email) = lower('inaciooofc@gmail.com')
on conflict (user_id)
do update set role = 'developer';
