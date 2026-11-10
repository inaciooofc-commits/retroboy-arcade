-- Execute no SQL Editor do projeto Supabase do Retroboy Arcade.
-- A conta precisa existir e o e-mail precisa ter sido confirmado.
-- Nunca inclua a senha neste arquivo.

do $$
begin
  if not exists (
    select 1 from auth.users
    where lower(email) = lower('inaciooofc@gmail.com')
      and email_confirmed_at is not null
  ) then
    raise exception 'A conta ainda não existe ou o e-mail ainda não foi confirmado. Cadastre e confirme primeiro.';
  end if;
end
$$;

insert into public.user_roles (user_id, role)
select id, 'developer'
from auth.users
where lower(email) = lower('inaciooofc@gmail.com')
  and email_confirmed_at is not null
on conflict (user_id)
do update set role = 'developer';
