import {useFormContext} from "react-hook-form";

export default function FormCheckbox(props) {
    const {register} = useFormContext()
    return <>
        {props.label}:{' '}
        <input type="checkbox" {...register(props.id)}/>
    <br/></>
}
